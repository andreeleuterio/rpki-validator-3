/**
 * The BSD License
 * <p>
 * Copyright (c) 2010-2018 RIPE NCC
 * All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of the RIPE NCC nor the names of its contributors may be
 * used to endorse or promote products derived from this software without
 * specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package net.ripe.rpki.validator3.storage.lmdb;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.ripe.rpki.validator3.storage.FSTCoder;
import net.ripe.rpki.validator3.storage.Lmdb;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IxMapTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private Lmdb lmdb;
    private IxMap<String> ixMap;

    private static final String LENGTH_INDEX = "length-index";

    @Before
    public void setUp() throws Exception {
        lmdb = LmdbTests.makeLmdb(tmp.newFolder().getAbsolutePath());
        ixMap = new IxMap<>(lmdb.getEnv(), "test", new FSTCoder<>(), ImmutableMap.of(LENGTH_INDEX, IxMapTest::stringLen));
    }

    @Test
    public void putAndGetByIndex() {
        Key ka = putAndGet("a");
        Key kaa = putAndGet("aa");
        Key kab = putAndGet("ab");
        Key kbbb = putAndGet("bbb");
        Key kxxx = putAndGet("xxx");

        try (Tx.Read<ByteBuffer> tx = lmdb.readTx()) {
            assertEquals(
                    Sets.newHashSet("a", "aa", "ab", "bbb", "xxx"),
                    new HashSet<>(ixMap.values(tx)));

            assertEquals(Sets.newHashSet("a"), new HashSet<>(getByLength(tx, 1)));
            assertEquals(Sets.newHashSet("aa", "ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }
    }

    @Test
    public void putAndDelete() {
        Key ka = putAndGet("a");
        Key kaa = putAndGet("aa");
        putAndGet("ab");
        Key kbbb = putAndGet("bbb");
        putAndGet("xxx");

        ixMap.delete(ka);

        try (Tx.Read<ByteBuffer> tx = lmdb.readTx()) {
            assertFalse(ixMap.get(tx, ka).isPresent());
            assertTrue(ixMap.get(tx, kaa).isPresent());
            assertTrue(ixMap.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet(), new HashSet<>(getByLength(tx, 1)));
            assertEquals(Sets.newHashSet("ab", "aa"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }

        ixMap.delete(kaa);

        try (Tx.Read<ByteBuffer> tx = lmdb.readTx()) {
            assertFalse(ixMap.get(tx, kaa).isPresent());
            assertTrue(ixMap.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet("ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("bbb", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }

        ixMap.delete(kbbb);

        try (Tx.Read<ByteBuffer> tx = lmdb.readTx()) {
            assertFalse(ixMap.get(tx, kbbb).isPresent());
            assertEquals(Sets.newHashSet("ab"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("xxx"), new HashSet<>(getByLength(tx, 3)));
        }
    }

    @Test
    public void putAndUpdate() {
        Key kaa = putAndGet("aa");
        Key kbb = putAndGet("bb");
        Key kxxx = putAndGet("xxx");

        ixMap.put(kaa, "qqq");
        try (Tx.Read<ByteBuffer> tx = lmdb.readTx()) {
            assertEquals("qqq", ixMap.get(tx, kaa).get());
            assertEquals(Sets.newHashSet("bb"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("qqq", "xxx"), new HashSet<>(getByLength(tx, 3)));
        }

        ixMap.put(kaa, "zz");
        try (Tx.Read<ByteBuffer> tx = lmdb.readTx()) {
            assertEquals("zz", ixMap.get(tx, kaa).get());
            assertEquals(Sets.newHashSet("zz", "bb"), new HashSet<>(getByLength(tx, 2)));
            assertEquals(Sets.newHashSet("xxx"), new HashSet<>(getByLength(tx, 3)));
        }
    }

    private List<String> getByLength(Tx.Read<ByteBuffer> tx, int i) {
        return ixMap.getByIndex(LENGTH_INDEX, tx, intKey(i));
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNull() {
        putAndGet(null);
    }

    @Test(expected = NullPointerException.class)
    public void putAndGetNullKey() {
        ixMap.put(null, "x");
    }

    private Key putAndGet(String v) {
        final Key key = key(UUID.randomUUID());
        ixMap.put(key, v);
        try (Tx.Read<ByteBuffer> tx = lmdb.readTx()) {
            assertEquals(v, ixMap.get(tx, key).get());
        }
        return key;
    }

    public static Key stringLen(String s) {
        return intKey(s.length());
    }

    public static Key intKey(int length) {
        final ByteBuffer bb = ByteBuffer.allocateDirect(Integer.BYTES);
        bb.putInt(length).flip();
        return new Key(bb);
    }

    static Key key(Object o) {
        return Key.of(o.toString().getBytes());
    }

}