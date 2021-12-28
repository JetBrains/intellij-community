/*
 * Copyright 2004-2020 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.jetbrains.mvstore;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.TemporaryDirectory;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.rules.InMemoryFsRule;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.AsciiString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.mvstore.type.ByteArrayDataType;
import org.jetbrains.mvstore.type.FixedByteArrayDataType;
import org.jetbrains.mvstore.type.IntDataType;
import org.jetbrains.mvstore.type.StringDataType;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static com.intellij.testFramework.assertions.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;

public class MVStoreTest {
  private static final Logger LOG = Logger.getInstance(MVStoreTest.class);
  // test using a regular FS as in production, not using InMemoryFsRule
  @Rule
  public final TemporaryDirectory tempDir = new TemporaryDirectory();

  @Rule
  public final InMemoryFsRule fsRule = new InMemoryFsRule();

  private static final BiConsumer<Throwable, MVStore> exceptionHandler = (e, store) -> {
    throw new AssertionError(e);
  };

  private static final MVMap.Builder<String, String> mapBuilder = new MVMap.Builder<String, String>()
    .keyType(StringDataType.INSTANCE)
    .valueType(StringDataType.INSTANCE);

  private static final MVMap.Builder<Integer, String> intToStringMapBuilder = new MVMap.Builder<Integer, String>()
    .keyType(IntDataType.INSTANCE)
    .valueType(StringDataType.INSTANCE);

  @Test
  public void testRemoveMapRollback() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore store = new MVStore.Builder().backgroundExceptionHandler(exceptionHandler).open(file)) {
      MVMap<String, String> map = store.openMap("test", mapBuilder);
      map.put("1", "Hello");
      store.commit();
      assertThat(store.hasMap("test")).isTrue();
      store.removeMap(map);
      store.rollback();
      assertThat(store.hasMap("test")).isTrue();
      map = store.openMap("test", mapBuilder);
      assertThat(map.get("1")).isEqualTo("Hello");
    }

    Files.delete(file);
    try (MVStore store = new MVStore.Builder().autoCommitDisabled().open(file)) {
      MVMap<String, String> map = store.openMap("test", mapBuilder);
      map.put("1", "Hello");
      store.commit();
      store.removeMap(map);
      store.rollback();
      assertThat(store.hasMap("test")).isTrue();
      map = store.openMap("test", mapBuilder);
      // the data will get back alive
      assertThat(map.get("1")).isEqualTo("Hello");
    }
  }

  @Test
  public void testVolatileMap() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore store = openStore(file)) {
      MVMap<String, String> map = store.openMap("test", mapBuilder);
      assertThat(map.isVolatile()).isFalse();
      map.setVolatile(true);
      assertThat(map.isVolatile()).isTrue();
      map.put("1", "Hello");
      assertThat(map.get("1")).isEqualTo("Hello");
      assertThat(map).hasSize(1);
    }

    try (MVStore store = new MVStore.Builder().readOnly().backgroundExceptionHandler(exceptionHandler).open(file)) {
      assertThat(store.hasMap("test")).isTrue();
      MVMap<String, String> map = store.openMap("test", mapBuilder);
      assertThat(map).isEmpty();
    }
  }

  @Test
  public void testEntrySet() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      MVMap<Integer, Integer> map = s.openMap("data", IntDataType.INSTANCE, IntDataType.INSTANCE);
      for (int i = 0; i < 20; i++) {
        map.put(i, i * 10);
      }
      int next = 0;
      for (Map.Entry<Integer, Integer> e : map.entrySet()) {
        assertThat(e.getKey()).isEqualTo(next);
        assertThat(e.getValue()).isEqualTo(next * 10);
        next++;
      }
    }
  }

  private static void assertEquals(Object expected, Object actual) {
    assertThat(actual).isEqualTo(expected);
  }

  private static void assertEquals(long expected, long actual) {
    assertThat(actual).isEqualTo(expected);
  }

  private static void assertEquals(int expected, int actual) {
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  public void testCompressEmptyPage() throws IOException {
    Path file = tempDir.newPath();
    MVStore store = new MVStore.Builder().backgroundExceptionHandler(exceptionHandler).cacheSize(100).compress().autoCommitBufferSize(10 * 1024).open(file);
    MVMap<String, String> map = store.openMap("test", mapBuilder);
    store.removeMap(map);
    store.commit();
    store.close();
    store = new MVStore.Builder().compress().open(file);
    store.close();
  }

  @Test
  public void testCompressed() throws IOException {
    Path file = tempDir.newPath();
    Random random = new Random(42);
    byte[] data = new byte[1000];
    random.nextBytes(data);

    long lastSize = 0;
    for (int level = 0; level <= 2; level++) {
      MVStore.Builder builder = new MVStore.Builder().compressionLevel(level);
      try (MVStore s = builder.open(file)) {
        MVMap<String, byte[]> map = s.openMap("data", StringDataType.INSTANCE, ByteArrayDataType.INSTANCE);
        for (int i = 0; i < 400; i++) {
          map.put(Integer.toHexString(i), data);
        }
      }

      long size = Files.size(file);
      if (level == 1) {
        assertThat(size).isLessThan(lastSize);
      }
      else if (level == 2) {
        // Result of LZ4HC maybe equal to LZ4
        assertThat(size).isLessThanOrEqualTo(lastSize);
      }

      lastSize = size;
      try (MVStore s = openStore(file)) {
        MVMap<String, byte[]> map = s.openMap("data", StringDataType.INSTANCE, ByteArrayDataType.INSTANCE);
        for (int i = 0; i < 400; i++) {
          assertThat(map.get(Integer.toHexString(i))).isEqualTo(data);
        }
      }

      Files.deleteIfExists(file);
    }
  }

  @Test
  public void testFileFormatExample() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      MVMap<Integer, String> map = s.openMap("data", IntDataType.INSTANCE, StringDataType.INSTANCE);
      for (int i = 0; i < 400; i++) {
        map.put(i, "Hello");
      }
      s.commit();
      for (int i = 0; i < 100; i++) {
        map.put(0, "Hi");
      }
      s.commit();
    }
  }

  @Test
  public void testMaxChunkLength() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      MVMap<Integer, byte[]> map = s.openMap("data", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
      map.put(0, new byte[2 * 1024 * 1024]);
      s.commit();
      map.put(1, new byte[10 * 1024]);
      s.commit();
      MVMap<Integer, byte[]> chunkMap = s.getChunkMap();
      Chunk c = Chunk.readMetadata(1, Unpooled.wrappedBuffer(chunkMap.get(1)));
      assertTrue(c.maxLen < Integer.MAX_VALUE);
      assertTrue(c.maxLenLive < Integer.MAX_VALUE);
    }
  }

  //@Test
  //public void testCacheInfo() throws IOException {
  //  Path file = tempDir.newPath();
  //  try (MVStore s = new MVStore.Builder().cacheSize(2).open(file)) {
  //    assertThat(s.getCacheSize()).isEqualTo(2);
  //    MVMap<Integer, byte[]> map = s.openMap("data", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
  //    byte[] data = new byte[1024];
  //    for (int i = 0; i < 1000; i++) {
  //      map.put(i, data);
  //      s.commit();
  //      if (i < 50) {
  //        assertThat(s.getCacheSizeUsed()).isEqualTo(0);
  //      }
  //      else if (i > 300) {
  //        assertTrue(s.getCacheSizeUsed() >= 1);
  //      }
  //    }
  //  }
  //  try (MVStore s = new MVStore.Builder().open(file)) {
  //    assertThat(s.getCacheSize()).isEqualTo(0);
  //    assertThat(s.getCacheSizeUsed()).isEqualTo(0);
  //  }
  //}

  @Test
  public void testVersionsToKeep() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = new MVStore.Builder().versionsToKeep(5).open(file)) {
      MVMap<Integer, Integer> map = s.openMap("data", IntDataType.INSTANCE, IntDataType.INSTANCE);
      for (int i = 0; i < 20; i++) {
        map.put(i, i);
        s.commit();
        long version = s.getCurrentVersion();
        if (version >= 6) {
          map.openVersion(version - 5);
          try {
            map.openVersion(version - 6);
            fail();
          }
          catch (IllegalArgumentException e) {
            // expected
          }
        }
      }
    }
  }

  @Test
  public void testVersionsToKeep2() throws IOException {
    Path file = fsRule.getFs().getPath("/db");
    try (MVStore s = new MVStore.Builder().autoCommitDisabled().versionsToKeep(2).open(file)) {
      MVMap<Integer, String> m = s.openMap("data", IntDataType.INSTANCE, StringDataType.INSTANCE);
      s.commit();
      assertThat(s.getCurrentVersion()).isEqualTo(1);
      m.put(1, "version 1");
      s.commit();
      assertThat(s.getCurrentVersion()).isEqualTo(2);
      m.put(1, "version 2");
      s.commit();
      assertThat(s.getCurrentVersion()).isEqualTo(3);
      m.put(1, "version 3");
      s.commit();
      m.put(1, "version 4");
      assertThat(m.openVersion(4).get(1)).isEqualTo("version 4");
      assertThat(m.openVersion(3).get(1)).isEqualTo("version 3");
      assertThat(m.openVersion(2).get(1)).isEqualTo("version 2");
      assertThatThrownBy(() -> {
        m.openVersion(1);
      }).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  public void testRemoveMap() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      MVMap<Integer, Integer> map = s.openMap("data", IntDataType.INSTANCE, IntDataType.INSTANCE);
      map.put(1, 1);
      assertThat(map.get(1).intValue()).isEqualTo(1);
      s.commit();

      s.removeMap(map);
      s.commit();

      map = s.openMap("data", IntDataType.INSTANCE, IntDataType.INSTANCE);
      assertTrue(map.isEmpty());
      map.put(2, 2);
    }
  }

  @Test
  public void testIsEmpty() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = new MVStore.Builder().pageSplitSize(50).open(file)) {
      Map<Integer, byte[]> m = s.openMap("data", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
      m.put(1, new byte[50]);
      m.put(2, new byte[50]);
      m.put(3, new byte[50]);
      m.remove(1);
      m.remove(2);
      m.remove(3);
      assertThat(m.size()).isEqualTo(0);
      assertTrue(m.isEmpty());
    }
  }

  @Test
  public void testCompactFully() throws IOException {
    Path file = tempDir.newPath();
    MVStore s = new MVStore.Builder().autoCommitDisabled().open(file);
    s.setRetentionTime(0);
    MVMap<Integer, String> m;
    for (int i = 0; i < 100; i++) {
      m = s.openMap("data" + i, IntDataType.INSTANCE, StringDataType.INSTANCE);
      m.put(0, "Hello World");
      s.commit();
    }
    for (int i = 0; i < 100; i += 2) {
      m = s.openMap("data" + i, IntDataType.INSTANCE, StringDataType.INSTANCE);
      s.removeMap(m);
      s.commit();
    }
    long sizeOld = s.getFileStore().size();
    s.compactMoveChunks();
    s.close();
    long sizeNew = s.getFileStore().size();
    assertThat(sizeNew).isLessThanOrEqualTo(sizeOld);
  }

  @Test
  public void testBackgroundExceptionListener() throws Exception {
    Path file = tempDir.newPath();
    AtomicReference<Throwable> exRef = new AtomicReference<>();
    MVStore s = new MVStore.Builder().
      backgroundExceptionHandler((e, store) -> exRef.set(e))
      .autoCommitDelay(10)
      .open(file);
    MVMap<Integer, String> m = s.openMap("data", IntDataType.INSTANCE, StringDataType.INSTANCE);
    s.getFileStore().close();
    try {
      m.put(1, "Hello");
      for (int i = 0; i < 200; i++) {
        if (exRef.get() != null) {
          break;
        }
        Thread.sleep(10);
        s.triggerAutoSave();
      }
      Throwable e = exRef.get();
      assertThat(e).isNotNull();
      assertThat(((MVStoreException)e).getErrorCode()).isEqualTo(MVStoreException.ERROR_WRITING_FAILED);
    }
    catch (MVStoreException e) {
      // sometimes it is detected right away
      assertThat(e.getErrorCode()).isEqualTo(MVStoreException.ERROR_CLOSED);
    }

    s.closeImmediately();
  }

  @Test
  public void testAtomicOperations() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      MVMap<Integer, byte[]> m = s.openMap("data", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);

      // putIfAbsent
      assertThat(m.putIfAbsent(1, new byte[1])).isNull();
      assertThat(m.putIfAbsent(1, new byte[2])).hasSize(1);
      assertThat(m.get(1)).hasSize(1);

      // replace
      assertThat(m.replace(2, new byte[2])).isNull();
      assertThat(m.get(2)).isNull();
      assertThat(m.replace(1, new byte[2])).hasSize(1);
      assertThat(m.replace(1, new byte[3])).hasSize(2);
      assertThat(m.replace(1, new byte[1])).hasSize(3);

      // replace with oldValue
      assertThat(m.replace(1, new byte[2], new byte[10])).isFalse();
      assertThat(m.replace(1, new byte[1], new byte[2])).isTrue();
      assertThat(m.replace(1, new byte[2], new byte[1])).isTrue();

      // remove
      assertThat(m.remove(1, new byte[2])).isFalse();
      assertThat(m.remove(1, new byte[1])).isTrue();
    }
  }


  @Test
  public void testWriteBuffer() throws IOException {
    Path file = tempDir.newPath();
    MVStore s;
    MVMap<Integer, byte[]> m;
    byte[] data = new byte[1000];
    long lastSize = 0;
    int len = 1000;
    for (int bs = 0; bs <= 1; bs++) {
      s = new MVStore.Builder().autoCommitBufferSize(bs).open(file);
      m = s.openMap("data", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
      for (int i = 0; i < len; i++) {
        m.put(i, data);
      }
      long size = s.getFileStore().size();
      assertTrue("last:" + lastSize + " now: " + size, size > lastSize);
      lastSize = size;
      s.close();
    }

    s = openStore(file);
    m = s.openMap("data", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
    assertTrue(m.containsKey(1));

    m.put(-1, data);
    s.commit();
    m.put(-2, data);
    s.close();

    s = openStore(file);
    m = s.openMap("data", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
    assertTrue(m.containsKey(-1));
    assertTrue(m.containsKey(-2));

    s.close();
  }


  @Test
  public void testWriteDelay() throws IOException, InterruptedException {
    Path file = tempDir.newPath();
    MVStore s = new MVStore.Builder().autoCommitDisabled().open(file);
    MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
    m.put(1, "1");
    s.commit();
    s.close();
    s = new MVStore.Builder().autoCommitDisabled().open(file);
    m = s.openMap("data", intToStringMapBuilder);
    assertEquals(1, m.size());
    s.close();

    Files.deleteIfExists(file);
    s = openStore(file);
    m = s.openMap("data", intToStringMapBuilder);
    m.put(1, "Hello");
    m.put(2, "World.");
    s.commit();
    s.close();

    s = new MVStore.Builder().autoCommitDelay(2).open(file);
    m = s.openMap("data", intToStringMapBuilder);
    assertEquals("World.", m.get(2));
    m.put(2, "World");
    s.commit();
    long v = s.getCurrentVersion();
    long time = System.nanoTime();
    m.put(3, "!");

    for (int i = 200; i > 0; i--) {
      if (s.getCurrentVersion() > v) {
        break;
      }
      long diff = System.nanoTime() - time;
      if (diff > TimeUnit.SECONDS.toNanos(1)) {
        fail("diff=" + TimeUnit.NANOSECONDS.toMillis(diff));
      }
      s.triggerAutoSave();
      Thread.sleep(10);
    }
    s.closeImmediately();

    s = openStore(file);
    m = s.openMap("data", intToStringMapBuilder);
    assertEquals("Hello", m.get(1));
    assertEquals("World", m.get(2));
    assertEquals("!", m.get(3));
    s.close();
  }

  @Test
  public void testFileFormatChange() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      s.setRetentionTime(Integer.MAX_VALUE);
      MVMap<Integer, Integer> m = s.openMap("test", IntDataType.INSTANCE, IntDataType.INSTANCE);
      m.put(1, 1);
      MVStore.StoreHeader header = s.getStoreHeader();
      int format = header.format;
      assertThat(format).isEqualTo(3);

      ByteBuf buf = PooledByteBufAllocator.DEFAULT.ioBuffer(2 * MVStore.BLOCK_SIZE);
      try {
        header.format = 12;
        header.write(buf);
        s.getFileStore().writeFully(buf, 0);
      }
      finally {
        buf.release();
      }
    }

    try {
      openStore(file).close();
      fail();
    }
    catch (MVStoreException e) {
      assertThat(e.getErrorCode()).isEqualTo(MVStoreException.ERROR_UNSUPPORTED_FORMAT);
    }
  }

  @Test
  public void testRecreateMap() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = new MVStore.Builder().backgroundExceptionHandler(exceptionHandler).open(file)) {
      MVMap<Integer, Integer> m = s.openMap("test", IntDataType.INSTANCE, IntDataType.INSTANCE);
      m.put(1, 1);
      s.commit();
      s.removeMap(m);
    }

    try (MVStore s = new MVStore.Builder().backgroundExceptionHandler(exceptionHandler).open(file)) {
      MVMap<Integer, Integer> m = s.openMap("test", IntDataType.INSTANCE, IntDataType.INSTANCE);
      assertThat(m.get(1)).isNull();
    }
  }

  @Test
  public void testRenameMapRollback() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      MVMap<Integer, Integer> map = s.openMap("hello", IntDataType.INSTANCE, IntDataType.INSTANCE);
      map.put(1, 10);
      long old = s.commit();
      s.renameMap(map, "world");
      map.put(2, 20);
      assertThat(map.getName()).isEqualTo(AsciiString.of("world"));
      s.rollbackTo(old);
      assertThat(map.getName()).isEqualTo(AsciiString.of("hello"));
      s.rollbackTo(0);
      assertThat(map.isClosed()).isTrue();
    }
  }

  private static @NotNull MVStore openStore(@NotNull Path file) throws IOException {
    return new MVStore.Builder().open(file);
  }

  //@Test
  //public void testCustomMapType() throws IOException {
  //  Path file = tempDir.newPath();
  //  try (MVStore s = openStore(file)) {
  //    Map<Long, Long> seq = s.openMap("data", new SequenceMap.Builder());
  //    StringBuilder buff = new StringBuilder();
  //    for (long x : seq.keySet()) {
  //      buff.append(x).append(';');
  //    }
  //    assertEquals("1;2;3;4;5;6;7;8;9;10;", buff.toString());
  //  }
  //}

  @Test
  public void testCacheSize() throws IOException {
    // flaky on Linux
    Assume.assumeFalse(UsefulTestCase.IS_UNDER_TEAMCITY);

    Path file = tempDir.newPath();
    Random random = new Random(42);
    try (MVStore s = new MVStore.Builder().autoCommitDisabled().open(file)) {
      // disable free space scanning
      s.setReuseSpace(false);
      MVMap<Integer, byte[]> map = s.openMap("test", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
      // add 10 MB of data
      for (int i = 0; i < 1024; i++) {
        byte[] value = new byte[10240];
        random.nextBytes(value);
        map.put(i, value);
      }
    }
    int[] expectedReadsForCacheSize = {350, 160, 60, 47, 47, 47, 47};
    long prevHitCount = 0;
    long prevMissCount = Long.MAX_VALUE;
    for (int cacheSize = 0; cacheSize <= 6; cacheSize += 1) {
      int cacheMb = 1 + 3 * cacheSize;
      try (MVStore s = new MVStore.Builder().autoCommitDisabled().cacheSize(cacheMb).recordCacheStats(true).open(file)) {
        //assertEquals(cacheMB, s.getCacheSize());
        MVMap<Integer, byte[]> map = s.openMap("test", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
        for (int i = 0; i < 1024; i += 128) {
          for (int j = 0; j < i; j++) {
            byte[] x = map.get(j);
            assertThat(x.length).isEqualTo(10240);
          }
        }

        CacheStats nonLeafCacheStats = s.getCacheStats(true);
        CacheStats leafCacheStats = s.getCacheStats(false);

        long readCount = s.getFileStore().getReadCount();

        LOG.debug(leafCacheStats.toString() + "; readCount=" + readCount);

        assertThat(nonLeafCacheStats.loadFailureCount()).isEqualTo(0);
        assertThat(nonLeafCacheStats.loadFailureRate()).isEqualTo(0);

        assertThat(leafCacheStats.loadFailureCount()).isEqualTo(0);
        assertThat(leafCacheStats.loadFailureRate()).isEqualTo(0);

        assertThat(nonLeafCacheStats.hitCount()).isEqualTo(0);
        assertThat(leafCacheStats.hitCount()).isGreaterThan(2380L);

        // more cache size -> more hits
        assertThat(leafCacheStats.hitCount()).isGreaterThanOrEqualTo(prevHitCount);
        prevHitCount = leafCacheStats.hitCount();

        // more cache size -> less misses
        assertThat(leafCacheStats.missCount()).isLessThanOrEqualTo(prevMissCount);
        prevMissCount = leafCacheStats.missCount();

        assertThat(readCount).isLessThanOrEqualTo(expectedReadsForCacheSize[cacheSize]);
      }
    }
  }

  @Test
  public void testConcurrentOpen() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = new MVStore.Builder().open(file)) {
      try {
        MVStore s1 = new MVStore.Builder().open(file);
        s1.close();
        fail();
      }
      catch (MVStoreException e) {
        // expected
      }

      try {
        MVStore s1 = new MVStore.Builder().readOnly().open(file);
        s1.close();
        fail();
      }
      catch (MVStoreException e) {
        // expected
      }
      assertThat(s.getFileStore().isReadOnly()).isFalse();
    }

    try (MVStore s = new MVStore.Builder().readOnly().open(file)) {
      assertTrue(s.getFileStore().isReadOnly());
    }
  }

  @Test
  public void testFileHeader() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      s.setRetentionTime(Integer.MAX_VALUE);
      long time = System.currentTimeMillis();
      MVStore.StoreHeader m = s.getStoreHeader();
      assertThat((int)m.format).isEqualTo(3);
      long creationTime = m.creationTime;
      assertThat(Math.abs(time - creationTime)).isLessThan(100);
    }
  }
  //
  //private static void forceWriteStoreHeader(MVStore s) {
  //  MVMap<Integer, Integer> map = s.openMap("dummy");
  //  map.put(10, 100);
  //  // this is to ensure the file header is overwritten
  //  // the header is written at least every 20 commits
  //  for (int i = 0; i < 30; i++) {
  //    if (i > 5) {
  //      s.setRetentionTime(0);
  //      // ensure that the next save time is different,
  //      // so that blocks can be reclaimed
  //      // (on Windows, resolution is 10 ms)
  //      sleep(1);
  //    }
  //    map.put(10, 110);
  //    s.commit();
  //  }
  //  s.removeMap(map);
  //  s.commit();
  //}
  //
  //private static void sleep(long ms) {
  //  // on Windows, need to sleep in some cases,
  //  // mainly because the milliseconds resolution of
  //  // System.currentTimeMillis is 10 ms.
  //  try {
  //    Thread.sleep(ms);
  //  }
  //  catch (InterruptedException e) {
  //    // ignore
  //  }
  //}
  //

  @Test
  public void testFileHeaderCorruption() throws Exception {
    Path file = tempDir.newPath();
    Files.deleteIfExists(file);
    MVStore.Builder builder = new MVStore.Builder().pageSplitSize(1000).autoCommitDisabled();
    try (MVStore s = builder.open(file)) {
      s.setRetentionTime(0);
      MVMap<Integer, byte[]> map = s.openMap("test", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
      map.put(0, new byte[100]);
      for (int i = 0; i < 10; i++) {
        map = s.openMap("test" + i, IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
        map.put(0, new byte[1000]);
        s.commit();
      }

      long size = Files.size(file);
      for (int i = 0; i < 100; i++) {
        map = s.openMap("test" + i, IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
        s.removeMap(map);
        s.commit();
        s.compact(100, 1);
        if (Files.size(file) <= size) {
          break;
        }
      }
      // the last chunk is at the end
      s.setReuseSpace(false);
      map = s.openMap("test2", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
      map.put(1, new byte[1000]);
    }

    int blockSize = 4 * 1024;
    // test corrupt file headers
    for (int i = 0; i <= blockSize; i += blockSize) {
      try (FileChannel fc = FileChannel.open(file, FileStore.RW)) {
        if (i == 0) {
          // corrupt the last block (the end header)
          fc.write(ByteBuffer.allocate(256), fc.size() - 256);
        }
        ByteBuffer buff = ByteBuffer.allocate(4 * 1024);
        fc.read(buff, i);
        String h = new String(buff.array(), StandardCharsets.UTF_8).trim();
        int idx = h.indexOf("fletcher:");
        int old = Character.digit(h.charAt(idx + "fletcher:".length()), 16);
        int bad = (old + 1) & 15;
        buff.put(idx + "fletcher:".length(),
                 (byte)Character.forDigit(bad, 16));

        // now intentionally corrupt first or both headers
        // note that headers may be overwritten upon successful opening
        for (int b = 0; b <= i; b += blockSize) {
          buff.rewind();
          fc.write(buff, b);
        }
      }

      if (i == 0) {
        // if the first header is corrupt, the second
        // header should be used
        try (MVStore s = openStore(file)) {
          MVMap<Integer, byte[]> map = s.openMap("test", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
          assertEquals(100, map.get(0).length);
          map = s.openMap("test2", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
          assertThat(map).doesNotContainKey(1);
        }
      }
      else {
        // both headers are corrupt
        try {
          openStore(file);
          fail();
        }
        catch (Exception e) {
          // expected
        }
      }
    }
  }

  @Test
  public void testIndexSkip() throws IOException {
    Path file = tempDir.newPath();
    MVStore s = new MVStore.Builder().pageSplitSize(4).open(file);
    MVMap<Integer, Integer> map = s.openMap("test", IntDataType.INSTANCE, IntDataType.INSTANCE);
    for (int i = 0; i < 100; i += 2) {
      map.put(i, 10 * i);
    }

    Cursor<Integer, Integer> c = map.cursor(50);
    // skip must reset the root of the cursor
    c.skip(10);
    for (int i = 70; i < 100; i += 2) {
      assertTrue(c.hasNext());
      assertEquals(i, c.next().intValue());
    }
    assertFalse(c.hasNext());

    for (int i = -1; i < 100; i++) {
      long index = map.getKeyIndex(i);
      if (i < 0 || (i % 2) != 0) {
        assertThat(index).isEqualTo(i < 0 ? -1 : -(i / 2) - 2);
      }
      else {
        assertThat(index).isEqualTo(i / 2);
      }
    }
    for (int i = -1; i < 60; i++) {
      Integer k = map.getKey(i);
      if (i < 0 || i >= 50) {
        assertNull(k);
      }
      else {
        assertThat(k.intValue()).isEqualTo(i * 2);
      }
    }
    // skip
    c = map.cursor(0);
    assertTrue(c.hasNext());
    assertEquals(0, c.next().intValue());
    c.skip(0);
    assertEquals(2, c.next().intValue());
    c.skip(1);
    assertEquals(6, c.next().intValue());
    c.skip(20);
    assertEquals(48, c.next().intValue());

    c = map.cursor(0);
    c.skip(20);
    assertEquals(40, c.next().intValue());

    c = map.cursor(0);
    assertEquals(0, c.next().intValue());

    assertEquals(12, map.keyList().indexOf(24));
    assertEquals(24, map.keyList().get(12).intValue());
    assertEquals(-14, map.keyList().indexOf(25));
    assertEquals(map.size(), map.keyList().size());
  }

  @Test
  public void testIndexSkipReverse() throws IOException {
    Path file = tempDir.newPath();
    MVStore s = new MVStore.Builder().pageSplitSize(4).open(file);
    MVMap<Integer, Integer> map = s.openMap("test", IntDataType.INSTANCE, IntDataType.INSTANCE);
    for (int i = 0; i < 100; i += 2) {
      map.put(i, 10 * i);
    }

    Cursor<Integer, Integer> c = map.cursor(50, null, true);
    // skip must reset the root of the cursor
    c.skip(10);
    for (int i = 30; i >= 0; i -= 2) {
      assertTrue(c.hasNext());
      assertEquals(i, c.next().intValue());
    }
    assertFalse(c.hasNext());
  }

  @Test
  public void testMinMaxNextKey() throws IOException {
    Path file = fsRule.getFs().getPath("/db");
    try (MVStore s = openStore(fsRule.getFs().getPath("/db"))) {
      MVMap<Integer, Integer> map = s.openMap("test", IntDataType.INSTANCE, IntDataType.INSTANCE);
      map.put(10, 100);
      map.put(20, 200);

      assertThat(map.firstKey()).isEqualTo(10);
      assertThat(map.lastKey()).isEqualTo(20);

      assertEquals(20, map.ceilingKey(15).intValue());
      assertEquals(20, map.ceilingKey(20).intValue());
      assertEquals(10, map.floorKey(15).intValue());
      assertEquals(10, map.floorKey(10).intValue());
      assertEquals(20, map.higherKey(10).intValue());
      assertEquals(10, map.lowerKey(20).intValue());

      assertEquals(10, map.ceilingKey(0).intValue());
      assertEquals(10, map.higherKey(0).intValue());
      assertNull(map.lowerKey(0));
      assertNull(map.floorKey(0));
    }

    for (int i = 3; i < 20; i++) {
      Files.deleteIfExists(file);
      try (MVStore s = new MVStore.Builder().pageSplitSize(4).open(file)) {
        MVMap<Integer, Integer> map = s.openMap("test", IntDataType.INSTANCE, IntDataType.INSTANCE);
        for (int j = 3; j < i; j++) {
          map.put(j * 2, j * 20);
        }
        if (i == 3) {
          assertThat(map.firstKey()).isNull();
          assertThat(map.lastKey()).isNull();
        }
        else {
          assertEquals(6, map.firstKey().intValue());
          int max = (i - 1) * 2;
          assertEquals(max, map.lastKey().intValue());

          for (int j = 0; j < i * 2 + 2; j++) {
            if (j > max) {
              assertNull(map.ceilingKey(j));
            }
            else {
              int ceiling = Math.max((j + 1) / 2 * 2, 6);
              assertEquals(ceiling, map.ceilingKey(j).intValue());
            }

            int floor = Math.min(max, Math.max(j / 2 * 2, 4));
            if (floor < 6) {
              assertNull(map.floorKey(j));
            }
            else {
              map.floorKey(j);
            }

            int lower = Math.min(max, Math.max((j - 1) / 2 * 2, 4));
            if (lower < 6) {
              assertNull(map.lowerKey(j));
            }
            else {
              assertEquals(lower, map.lowerKey(j).intValue());
            }

            int higher = Math.max((j + 2) / 2 * 2, 6);
            if (higher > max) {
              assertNull(map.higherKey(j));
            }
            else {
              assertEquals(higher, map.higherKey(j).intValue());
            }
          }
        }
      }
    }
  }

  //@Test
  //public void testStoreVersion() throws IOException {
  //  Path file = tempDir.newPath();
  //  MVStore store = openStore(file);
  //  assertEquals(0, store.getCurrentVersion());
  //  //assertEquals(0, store.getStoreVersion());
  //  store.setStoreVersion(0);
  //  store.commit();
  //  store.setStoreVersion(1);
  //  store.closeImmediately();
  //
  //  try (MVStore s = MVStore.open(fileName)) {
  //    assertEquals(1, s.getCurrentVersion());
  //    assertEquals(0, s.getStoreVersion());
  //    s.setStoreVersion(1);
  //  }
  //
  //  try (MVStore s = MVStore.open(fileName)) {
  //    assertEquals(2, s.getCurrentVersion());
  //    assertEquals(1, s.getStoreVersion());
  //  }
  //}

  @Test
  public void testIterateOldVersion() throws IOException {
    Path file = fsRule.getFs().getPath("/db");
    try (MVStore s = new MVStore.Builder().open(file)) {
      Map<Integer, Integer> map = s.openMap("test", IntDataType.INSTANCE, IntDataType.INSTANCE);
      int len = 100;
      for (int i = 0; i < len; i++) {
        map.put(i, 10 * i);
      }
      int count = 0;
      MVStore.TxCounter txCounter = s.registerVersionUsage();
      try {
        Iterator<Integer> it = map.keySet().iterator();
        s.commit();
        for (int i = 0; i < len; i += 2) {
          map.remove(i);
        }
        while (it.hasNext()) {
          it.next();
          count++;
        }
      }
      finally {
        s.deregisterVersionUsage(txCounter);
      }
      assertEquals(len, count);
    }
  }

  //@Test
  //public void testObjects() throws IOException {
  //  Path file = tempDir.newPath();
  //  Files.deleteIfExists(file);
  //  try (MVStore s = new MVStore.Builder().open(file)) {
  //    MVMap<Integer, String> map = s.openMap("test", intToStringMapBuilder);
  //    map.put(1, "Hello");
  //    map.put("2", 200);
  //    map.put(new Object[1], new Object[]{1, "2"});
  //  }
  //
  //  try (MVStore s = new MVStore.Builder().open(file)) {
  //    Map<Object, Object> map = s.openMap("test");
  //    assertEquals("Hello", map.get(1).toString());
  //    assertEquals(200, ((Integer)map.get("2")).intValue());
  //    Object[] x = (Object[])map.get(new Object[1]);
  //    assertEquals(2, x.length);
  //    assertEquals(1, ((Integer)x[0]).intValue());
  //    assertEquals("2", (String)x[1]);
  //  }
  //}

  @Test
  public void testExample() throws IOException {
    Path file = tempDir.newPath();
    //Path file = Paths.get("/Volumes/data/test.db");
    //Files.deleteIfExists(file);

    try (MVStore s = openStore(file)) {
      // create/get the map named "data"
      MVMap<Integer, String> map = s.openMap("data", intToStringMapBuilder);

      // add and read some data
      map.put(1, "Hello World");
      // LOG.debug(map.get(1));
    }
    try (MVStore s = openStore(file)) {
      MVMap<Integer, String> map = s.openMap("data", intToStringMapBuilder);
      assertEquals("Hello World", map.get(1));
    }
  }

  @Test
  public void testFixedByteArrayKey() throws IOException {
    Path file = fsRule.getFs().getPath("/db");
    //Path file = Paths.get("/Volumes/data/test.db");
    //Files.deleteIfExists(file);

    MVMap.Builder<byte[], String> mapBuilder = new MVMap.Builder<byte[], String>()
      .keyType(new FixedByteArrayDataType(2))
      .valueType(StringDataType.INSTANCE);

    MVStore.Builder storeBuilder = new MVStore.Builder();
    try (MVStore s = storeBuilder.open(file)) {
      MVMap<byte[], String> map = s.openMap("data", mapBuilder);
      map.put(new byte[]{0, 1}, "Hello World");
    }
    try (MVStore s = storeBuilder.open(file)) {
      MVMap<byte[], String> map = s.openMap("data", mapBuilder);
      assertThat(map.get(new byte[]{0, 1})).isEqualTo("Hello World");
      map.put(new byte[]{0, 1}, "Hello World");
      // test insert after serialization
      map.put(new byte[]{0, 2}, "Hello World");
    }

    try (MVStore s = storeBuilder.open(file)) {
      MVMap<byte[], String> map = s.openMap("data", mapBuilder);
      assertThat(map.get(new byte[]{0, 1})).isEqualTo("Hello World");
      map.put(new byte[]{0, 1}, "Hello World");
      // test remove after serialization
      map.remove(new byte[]{0, 1});
      assertThat(map.get(new byte[]{0, 1})).isNull();
    }
  }

  @Test
  public void testExampleMvcc() throws IOException {
    Path file = tempDir.newPath();
    Files.deleteIfExists(file);

    // open the store (in-memory if fileName is null)
    try (MVStore s = openStore(file)) {
      // create/get the map named "data"
      MVMap<Integer, String> map = s.openMap("data", intToStringMapBuilder);

      // add some data
      map.put(1, "Hello");
      map.put(2, "World");

      // get the current version, for later use
      long oldVersion = s.getCurrentVersion();

      // from now on, the old version is read-only
      s.commit();

      // more changes, in the new version
      // changes can be rolled back if required
      // changes always go into "head" (the newest version)
      map.put(1, "Hi");
      map.remove(2);

      // access the old data (before the commit)
      MVMap<Integer, String> oldMap =
        map.openVersion(oldVersion);

      // print the old version (can be done
      // concurrently with further modifications)
      // this will print "Hello" and "World":
      // LOG.debug(oldMap.get(1));
      assertEquals("Hello", oldMap.get(1));
      // LOG.debug(oldMap.get(2));
      assertEquals("World", oldMap.get(2));

      // print the newest version ("Hi")
      // LOG.debug(map.get(1));
      assertEquals("Hi", map.get(1));
    }
  }

  @Test
  public void testOpenStoreCloseLoop() throws IOException {
    Path file = tempDir.newPath();
    for (int k = 0; k < 1; k++) {
      // long t = System.nanoTime();
      for (int j = 0; j < 3; j++) {
        try (MVStore s = openStore(file)) {
          Map<String, Integer> m = s.openMap("data", StringDataType.INSTANCE, IntDataType.INSTANCE);
          for (int i = 0; i < 3; i++) {
            Integer x = m.get("value");
            m.put("value", x == null ? 0 : x + 1);
            s.commit();
          }
        }
      }
      // LOG.debug("open/close: " +
      //        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t));
      // LOG.debug("size: " + FileUtils.size(fileName));
    }
  }

  @Test
  public void testOldVersion() throws IOException {
    Path file = fsRule.getFs().getPath("/db");

    for (int op = 0; op <= 1; op++) {
      for (int i = 0; i < 5; i++) {
        Files.deleteIfExists(file);
        try (MVStore s = new MVStore.Builder().versionsToKeep(Integer.MAX_VALUE).open(file)) {
          MVMap<String, String> m;
          m = s.openMap("data", mapBuilder);
          for (int j = 0; j < 5; j++) {
            if (op == 1) {
              m.put("1", String.valueOf(s.getCurrentVersion()));
            }
            s.commit();
          }
          for (int j = 0; j < s.getCurrentVersion(); j++) {
            MVMap<String, String> old = m.openVersion(j);
            if (op == 1) {
              assertEquals(String.valueOf(j), old.get("1"));
            }
          }
        }
      }
    }
  }

  @Test
  public void testVersion() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = new MVStore.Builder().autoCommitDisabled().versionsToKeep(100).open(file)) {
      s.setRetentionTime(Integer.MAX_VALUE);
      MVMap<String, String> m = s.openMap("data", mapBuilder);
      s.commit();
      long first = s.getCurrentVersion();
      assertThat(first).isEqualTo(1);
      m.put("0", "test");
      s.commit();
      m.put("1", "Hello");
      m.put("2", "World");
      for (int i = 10; i < 20; i++) {
        m.put(String.valueOf(i), "data");
      }
      long old = s.getCurrentVersion();
      s.commit();
      m.put("1", "Hallo");
      m.put("2", "Welt");
      MVMap<String, String> mFirst;
      mFirst = m.openVersion(first);
      // openVersion() should restore map at last known state of the version specified
      // not at the first known state, as it was before
      assertEquals(1, mFirst.size());
      MVMap<String, String> mOld;
      assertEquals("Hallo", m.get("1"));
      assertEquals("Welt", m.get("2"));
      mOld = m.openVersion(old);
      assertEquals("Hello", mOld.get("1"));
      assertEquals("World", mOld.get("2"));
      assertTrue(mOld.isReadOnly());
      long old3 = s.getCurrentVersion();
      assertThat(old3).isEqualTo(3);
      s.commit();

      // the old version is still available
      assertEquals("Hello", mOld.get("1"));
      assertEquals("World", mOld.get("2"));

      mOld = m.openVersion(old3);
      assertEquals("Hallo", mOld.get("1"));
      assertEquals("Welt", mOld.get("2"));

      m.put("1", "Hi");
      assertEquals("Welt", m.remove("2"));
    }

    try (MVStore s = openStore(file)) {
      MVMap<String, String> m = s.openMap("data", mapBuilder);
      assertEquals("Hi", m.get("1"));
      assertEquals(null, m.get("2"));

      try {
        m.openVersion(-3);
        fail();
      }
      catch (IllegalArgumentException e) {
        // expected
      }
    }
  }

  @Test
  public void testTruncateFile() throws IOException, InterruptedException {
    Path file = tempDir.newPath();
    Random random = new Random(42);
    try (MVStore s = new MVStore.Builder().compressionLevel(0).open(file)) {
      MVMap<Integer, byte[]> m = s.openMap("data", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
      byte[] data = new byte[10_000];
      random.nextBytes(data);
      for (int i = 1; i < 10; i++) {
        m.put(i, data);
        s.commit();
      }
    }
    long fileSize = Files.size(file);
    try (MVStore s = new MVStore.Builder().autoCommitDisabled().compressionLevel(0).open(file)) {
      s.setRetentionTime(0);
      // remove 75%
      MVMap<Integer, byte[]> m = s.openMap("data", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
      for (int i = 0; i < 10; i++) {
        if (i % 4 != 0) {
          m.remove(i);
          s.commit();
        }
      }
      //assertThat(s.compact(100, 50 * 1024)).isTrue();
      // compaction alone will not guarantee file size reduction
      s.compactMoveChunks();
    }
    long len2 = Files.size(file);
    assertThat(fileSize).isGreaterThanOrEqualTo(len2);
  }

  @Test
  public void testFastDelete() throws IOException {
    Path file = fsRule.getFs().getPath("/db");
    try (MVStore s = new MVStore.Builder().compressionLevel(0).pageSplitSize(700).open(file)) {
      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      for (int i = 0; i < 1000; i++) {
        m.put(i, "Hello World");
        assertThat(m.size()).isEqualTo(i + 1);
        //LOG.debug(i + " " + s.getUnsavedMemory());
      }
      assertThat(m.size()).isEqualTo(1000);
      // memory calculations were adjusted, so as this out-of-the-thin-air number
      assertThat(s.getUnsavedMemory()).isEqualTo(57600);
      assertThat(m.getRootPage().getUnsavedMemory()).isEqualTo(56872);
      s.commit();
      assertThat(s.getFileStore().getWriteCount()).isEqualTo(2);
    }

    try (MVStore s = openStore(file)) {
      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      assertThat(s.getFileStore().getReadCount()).isEqualTo(6);
      m.clear();
      assertThat(m).isEmpty();
      s.commit();
      // ensure only nodes are read, but not leaves
      assertThat(s.getFileStore().getReadCount()).isEqualTo(15);
      assertThat(s.getFileStore().getWriteCount()).isLessThan(5);
    }
  }

  @Test
  public void testRollback() throws IOException {
    Path file = fsRule.getFs().getPath("/db");
    try (MVStore s = openStore(file)) {
      MVMap<Integer, Integer> m = s.openMap("m", IntDataType.INSTANCE, IntDataType.INSTANCE);
      m.put(1, -1);
      s.commit();
      for (int i = 0; i < 10; i++) {
        m.put(1, i);
        s.rollback();
        assertEquals(i - 1, m.get(1).intValue());
        m.put(1, i);
        s.commit();
      }
    }
  }

  @Test
  public void testRollbackStored() throws IOException {
    Path file = tempDir.newPath();
    long v2;
    try (MVStore s = openStore(file)) {
      assertEquals(45000, s.getRetentionTime());
      s.setRetentionTime(0);
      assertEquals(0, s.getRetentionTime());
      s.setRetentionTime(45000);
      assertEquals(45000, s.getRetentionTime());
      assertEquals(0, s.getCurrentVersion());
      assertFalse(s.hasUnsavedChanges());
      MVMap<String, String> m = s.openMap("data", mapBuilder);
      assertTrue(s.hasUnsavedChanges());
      s.openMap("data0", mapBuilder);
      m.put("1", "Hello");
      assertEquals(1, s.commit());
      s.rollbackTo(1);
      assertEquals(1, s.getCurrentVersion());
      assertEquals("Hello", m.get("1"));
      // so a new version is created
      m.put("1", "Hello");

      v2 = s.commit();
      assertEquals(2, v2);
      assertEquals(2, s.getCurrentVersion());
      assertFalse(s.hasUnsavedChanges());
      assertEquals("Hello", m.get("1"));
    }

    try (MVStore s = openStore(file)) {
      s.setRetentionTime(45000);
      assertEquals(2, s.getCurrentVersion());
      MVMap<AsciiString, MapMetadata> meta = s.getMetaMap();
      MVMap<String, String> m = s.openMap("data", mapBuilder);
      assertFalse(s.hasUnsavedChanges());
      assertEquals("Hello", m.get("1"));
      MVMap<String, String> m0 = s.openMap("data0", mapBuilder);
      MVMap<String, String> m1 = s.openMap("data1", mapBuilder);
      m.put("1", "Hallo");
      m0.put("1", "Hallo");
      m1.put("1", "Hallo");
      assertEquals("Hallo", m.get("1"));
      assertEquals("Hallo", m1.get("1"));
      assertTrue(s.hasUnsavedChanges());
      s.rollbackTo(v2);
      assertFalse(s.hasUnsavedChanges());
      assertNull(meta.get(AsciiString.of("data1")));
      assertNull(m0.get("1"));
      assertEquals("Hello", m.get("1"));
      // no changes - no real commit here
      assertEquals(2, s.commit());
    }

    long v3;
    try (MVStore s = openStore(file)) {
      s.setRetentionTime(45000);
      assertEquals(2, s.getCurrentVersion());
      MVMap<AsciiString, MapMetadata> meta = s.getMetaMap();
      assertNotNull(meta.get(AsciiString.of("data")));
      assertNotNull(meta.get(AsciiString.of("data0")));
      assertNull(meta.get(AsciiString.of("data1")));
      MVMap<String, String> m = s.openMap("data", mapBuilder);
      MVMap<String, String> m0 = s.openMap("data0", mapBuilder);
      assertNull(m0.get("1"));
      assertEquals("Hello", m.get("1"));
      assertFalse(m0.isReadOnly());
      m.put("1", "Hallo");
      s.commit();
      v3 = s.getCurrentVersion();
      assertEquals(3, v3);
    }

    try (MVStore s = openStore(file)) {
      s.setRetentionTime(45000);
      assertEquals(3, s.getCurrentVersion());
      MVMap<String, String> m = s.openMap("data", mapBuilder);
      m.put("1", "Hi");
    }

    try (MVStore s = openStore(file)) {
      s.setRetentionTime(45000);
      MVMap<String, String> m = s.openMap("data", mapBuilder);
      assertEquals("Hi", m.get("1"));
      s.rollbackTo(v3);
      assertEquals("Hallo", m.get("1"));
    }

    try (MVStore s = openStore(file)) {
      s.setRetentionTime(45000);
      MVMap<String, String> m = s.openMap("data", mapBuilder);
      assertEquals("Hallo", m.get("1"));
    }
  }

  @Test
  public void testRollbackInMemory() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = new MVStore.Builder().pageSplitSize(5).autoCommitDisabled().open(file)) {
      assertEquals(0, s.getCurrentVersion());
      MVMap<String, String> m = s.openMap("data", mapBuilder);
      s.rollbackTo(0);
      assertTrue(m.isClosed());
      assertEquals(0, s.getCurrentVersion());
      m = s.openMap("data", mapBuilder);

      MVMap<String, String> m0 = s.openMap("data0", mapBuilder);
      MVMap<String, String> m2 = s.openMap("data2", mapBuilder);
      m.put("1", "Hello");
      for (int i = 0; i < 10; i++) {
        m2.put(String.valueOf(i), "Test");
      }
      long v1 = s.commit();
      assertEquals(1, v1);
      assertEquals(1, s.getCurrentVersion());
      MVMap<String, String> m1 = s.openMap("data1", mapBuilder);
      assertEquals("Test", m2.get("1"));
      m.put("1", "Hallo");
      m0.put("1", "Hallo");
      m1.put("1", "Hallo");
      m2.clear();
      assertEquals("Hallo", m.get("1"));
      assertEquals("Hallo", m1.get("1"));
      s.rollbackTo(v1);
      assertEquals(1, s.getCurrentVersion());
      for (int i = 0; i < 10; i++) {
        assertEquals("Test", m2.get(String.valueOf(i)));
      }
      assertEquals("Hello", m.get("1"));
      assertNull(m0.get("1"));
      assertTrue(m1.isClosed());
      assertFalse(m0.isReadOnly());
    }
  }

  //@Test
  //public void testMeta() throws IOException {
  //  Path file = tempDir.newPath();
  //  try (MVStore s = openStore(file)) {
  //    s.setRetentionTime(Integer.MAX_VALUE);
  //    MVMap<AsciiString, MapMetadata> m = s.getMetaMap();
  //    assertEquals("[]", s.getMapNames().toString());
  //    MVMap<String, String> data = s.openMap("data", mapBuilder);
  //    data.put("1", "Hello");
  //    data.put("2", "World");
  //    s.commit();
  //    assertEquals(1, s.getCurrentVersion());
  //
  //    assertEquals("[data]", s.getMapNames().toString());
  //    assertEquals("data", s.getMapName(data.getId()));
  //    assertNull(s.getMapName(s.getMetaMap().getId()));
  //    assertNull(s.getMapName(data.getId() + 1));
  //
  //    MapMetadata id = s.getMetaMap().get(AsciiString.of("data"));
  //    assertEquals(6, id.id);
  //    assertEquals("Hello", data.put("1", "Hallo"));
  //    s.commit();
  //    assertEquals(id, m.get(AsciiString.of("data")));
  //    MVMap<Integer, Long> layoutMap = s.getLayoutMap();
  //    assertTrue(m.get(DataUtils.META_ROOT + id).length() > 0);
  //    assertTrue(m.containsKey(DataUtils.META_CHUNK + "1"));
  //
  //    assertEquals(2, s.getCurrentVersion());
  //
  //    s.rollbackTo(1);
  //    assertEquals("Hello", data.get("1"));
  //    assertEquals("World", data.get("2"));
  //  }
  //}
  //
  //@Test
  //public void testInMemory() {
  //  for (int j = 0; j < 1; j++) {
  //    try (MVStore s = openStore(null)) {
  //      // s.setMaxPageSize(10);
  //      int len = 100;
  //      // TreeMap<Integer, String> m = new TreeMap<Integer, String>();
  //      // HashMap<Integer, String> m = New.hashMap();
  //      MVMap<Integer, String> m = s.openMap("data", mapBuilder);
  //      for (int i = 0; i < len; i++) {
  //        assertNull(m.put(i, "Hello World"));
  //      }
  //      for (int i = 0; i < len; i++) {
  //        assertEquals("Hello World", m.get(i));
  //      }
  //      for (int i = 0; i < len; i++) {
  //        assertEquals("Hello World", m.remove(i));
  //      }
  //      assertEquals(null, m.get(0));
  //      assertEquals(0, m.size());
  //    }
  //  }
  //}
  //
  //@Test
  //public void testLargeImport() throws IOException {
  //  Path file = tempDir.newPath();
  //  int len = 1000;
  //  for (int j = 0; j < 5; j++) {
  //    Files.deleteIfExists(file);
  //    try (MVStore s = new MVStore.Builder().pageSplitSize(40).open(file)) {
  //      MVMap<Integer, Object[]> m = s.openMap("data",
  //                                             new MVMap.Builder<Integer, Object[]>()
  //                                               .valueType(new RowDataType(new DataType[]{
  //                                                 new ObjectDataType(),
  //                                                 StringDataType.INSTANCE,
  //                                                 StringDataType.INSTANCE})));
  //
  //      // Profiler prof = new Profiler();
  //      // prof.startCollecting();
  //      // long t = System.nanoTime();
  //      for (int i = 0; i < len; ) {
  //        Object[] o = new Object[3];
  //        o[0] = i;
  //        o[1] = "Hello World";
  //        o[2] = "World";
  //        m.put(i, o);
  //        i++;
  //        if (i % 10000 == 0) {
  //          s.commit();
  //        }
  //      }
  //    }
  //    // LOG.debug(prof.getTop(5));
  //    // LOG.debug("store time " +
  //    //         TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t));
  //    // LOG.debug("store size " +
  //    //         FileUtils.size(fileName));
  //  }
  //}
  //

  @Test
  public void testBtreeStore() throws IOException {
    Path file = tempDir.newPath();
    MVStore store = openStore(file);
    store.close();

    int count = 2000;
    try (MVStore s = openStore(file)) {
      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      for (int i = 0; i < count; i++) {
        assertNull(m.put(i, "hello " + i));
        assertEquals("hello " + i, m.get(i));
      }
      s.commit();
      assertEquals("hello 0", m.remove(0));
      assertNull(m.get(0));
      for (int i = 1; i < count; i++) {
        assertEquals("hello " + i, m.get(i));
      }
    }

    try (MVStore s = openStore(file)) {
      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      assertNull(m.get(0));
      for (int i = 1; i < count; i++) {
        assertEquals("hello " + i, m.get(i));
      }
      for (int i = 1; i < count; i++) {
        m.remove(i);
      }
      s.commit();
      assertNull(m.get(0));
      for (int i = 0; i < count; i++) {
        assertNull(m.get(i));
      }
    }
  }

  @Test
  public void testCompactMapNotOpen() throws IOException {
    Path file = tempDir.newPath();
    int factor = 100;
    try (MVStore s = new MVStore.Builder().compressionLevel(0).autoCommitDisabled().pageSplitSize(1000).autoCompactFillRate(90).open(file)) {
      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      for (int j = 0; j < 10; j++) {
        for (int i = j * factor; i < 10 * factor; i++) {
          m.put(i, "Hello" + j);
        }
        s.commit();
      }
    }

    try (MVStore s = new MVStore.Builder().autoCommitDisabled().autoCompactFillRate(90).open(file)) {
      s.setRetentionTime(0);

      int chunkCount1 = s.getChunkMap().size();
      s.compact(80, 1);
      s.compact(80, 1);

      int chunkCount2 = s.getChunkMap().size();
      assertThat(chunkCount2).isGreaterThanOrEqualTo(chunkCount1);

      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      for (int i = 0; i < 10; i++) {
        s.commit();
        boolean result = s.compact(50, 50 * 1024);
        if (!result) {
          break;
        }
      }
      s.compact(50, 1024);
      assertThat(s.compact(50, 1024)).isFalse();

      int chunkCount3 = s.getChunkMap().size();

      assertTrue(chunkCount1 + ">" + chunkCount2 + ">" + chunkCount3,
                 chunkCount3 < chunkCount1);

      for (int i = 0; i < 10 * factor; i++) {
        assertEquals("Hello" + (i / factor), m.get(i));
      }
    }
  }


  @Test
  public void testCompact() throws IOException {
    Path file = tempDir.newPath();

    long initialLength = 0;
    for (int j = 0; j < 20; j++) {
      try (MVStore s = new MVStore.Builder()
        .pageSplitSize(1000)
        .autoCommitDisabled()
        .compressionLevel(0)
        .backgroundExceptionHandler(exceptionHandler)
        .open(file)) {
        s.setRetentionTime(0);
        MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
        for (int i = 0; i < 100; i++) {
          m.put(j + i, "Hello " + j);
        }

        LOG.debug(j + " Before - fill rate: " + s.getFillRate() + "%, chunks fill rate: "
              + s.getChunksFillRate() + ", len: " + Files.size(file));
        s.compact(80, 2048);
        s.compactMoveChunks();

        LOG.debug(j + " After  - fill rate: " + s.getFillRate() + "%, chunks fill rate: "
                           + s.getChunksFillRate() + ", len: " + Files.size(file));
      }

      long fileSize = Files.size(file);
      LOG.debug("   len:" + fileSize);
      if (initialLength == 0) {
        initialLength = fileSize;
      }
      else {
        assertThat(fileSize).isLessThanOrEqualTo(initialLength * 3);
      }
    }

    // long len = Files.size(file);
    // LOG.debug("len0: " + len);
    try (MVStore s = new MVStore.Builder().compressionLevel(0).open(file)) {
      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      for (int i = 0; i < 100; i++) {
        m.remove(i);
      }
      s.compact(80, 1024);
    }

    // len = Files.size(file);
    // LOG.debug("len1: " + len);
    try (MVStore s = new MVStore.Builder().compressionLevel(0).open(file)) {
      s.openMap("data", intToStringMapBuilder);
      s.compact(80, 1024);
    }
    // len = Files.size(file);
    // LOG.debug("len2: " + len);
  }

  @Test
  public void testCompact2() throws IOException {
    Path file = tempDir.newPath();
    long initialLength = 0;
    for (int j = 0; j < 10; j++) {
      try (MVStore s = new MVStore.Builder()
        .autoCommitDisabled()
        .compressionLevel(0)
        .backgroundExceptionHandler(exceptionHandler)
        .open(file)) {
        s.setRetentionTime(0);
        MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
        for (int i = 0; i < 5; i++) {
          m.put(i, "Hello " + j);
        }

        s.commit();

        LOG.debug(j + " Before - fill rate: " + s.getFillRate() + "%, chunks fill rate: "
              + s.getChunksFillRate() + ", len: " + Files.size(file));
        s.compact(80, 2048);
        s.compactMoveChunks();

        LOG.debug(j + " After  - fill rate: " + s.getFillRate() + "%, chunks fill rate: "
                           + s.getChunksFillRate() + ", len: " + Files.size(file));
      }

      long fileSize = Files.size(file);
      LOG.debug("   len:" + fileSize);
      if (initialLength == 0) {
        initialLength = fileSize;
      }
      else {
        assertThat(fileSize).isLessThanOrEqualTo(initialLength * 3);
      }
    }
  }

  @Test
  public void testReuseSpace() throws InterruptedException, IOException {
    Path file = tempDir.newPath();
    long initialLength = 0;
    for (int j = 0; j < 20; j++) {
      Thread.sleep(2);
      try (MVStore s = new MVStore.Builder().compressionLevel(0).open(file)) {
        s.setRetentionTime(0);
        MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
        for (int i = 0; i < 10; i++) {
          m.put(i, "Hello");
        }
        s.commit();
        for (int i = 0; i < 10; i++) {
          assertEquals("Hello", m.get(i));
          assertEquals("Hello", m.remove(i));
        }
      }
      long len = Files.size(file);
      if (initialLength == 0) {
        initialLength = len;
      }
      else {
        assertTrue("len: " + len + " initial: " + initialLength + " j: " + j,
                   len <= initialLength * 3);
      }
    }
  }
  //
  //@Test
  //public void testRandom() {
  //  Path file = tempDir.newPath();
  //  Files.deleteIfExists(file);
  //  try (MVStore s = openStore(file)) {
  //    MVMap<Integer, Integer> m = s.openMap("data", mapBuilder);
  //    TreeMap<Integer, Integer> map = new TreeMap<>();
  //    Random r = new Random(1);
  //    int operationCount = 1000;
  //    int maxValue = 30;
  //    Integer expected, got;
  //    for (int i = 0; i < operationCount; i++) {
  //      int k = r.nextInt(maxValue);
  //      int v = r.nextInt();
  //      boolean compareAll;
  //      switch (r.nextInt(3)) {
  //        case 0:
  //          log(i + ": put " + k + " = " + v);
  //          expected = map.put(k, v);
  //          got = m.put(k, v);
  //          if (expected == null) {
  //            assertNull(got);
  //          }
  //          else {
  //            assertEquals(expected, got);
  //          }
  //          compareAll = true;
  //          break;
  //        case 1:
  //          log(i + ": remove " + k);
  //          expected = map.remove(k);
  //          got = m.remove(k);
  //          if (expected == null) {
  //            assertNull(got);
  //          }
  //          else {
  //            assertEquals(expected, got);
  //          }
  //          compareAll = true;
  //          break;
  //        default:
  //          Integer a = map.get(k);
  //          Integer b = m.get(k);
  //          if (a == null || b == null) {
  //            assertTrue(a == b);
  //          }
  //          else {
  //            assertEquals(a.intValue(), b.intValue());
  //          }
  //          compareAll = false;
  //          break;
  //      }
  //      if (compareAll) {
  //        Iterator<Integer> it = m.keyIterator(null);
  //        for (Integer integer : map.keySet()) {
  //          assertTrue(it.hasNext());
  //          expected = integer;
  //          got = it.next();
  //          assertEquals(expected, got);
  //        }
  //        assertFalse(it.hasNext());
  //      }
  //    }
  //  }
  //}
  //
  //@Test
  //public void testKeyValueClasses() {
  //  Path file = tempDir.newPath();
  //  try (MVStore s = openStore(file)) {
  //    MVMap<Integer, String> is = s.openMap("intString");
  //    is.put(1, "Hello");
  //    MVMap<Integer, Integer> ii = s.openMap("intInt");
  //    ii.put(1, 10);
  //    MVMap<String, Integer> si = s.openMap("stringInt");
  //    si.put("Test", 10);
  //    MVMap<String, String> ss = s.openMap("stringString");
  //    ss.put("Hello", "World");
  //  }
  //
  //  try (MVStore s = openStore(file)) {
  //    MVMap<Integer, String> is = s.openMap("intString");
  //    assertEquals("Hello", is.get(1));
  //    MVMap<Integer, Integer> ii = s.openMap("intInt");
  //    assertEquals(10, ii.get(1).intValue());
  //    MVMap<String, Integer> si = s.openMap("stringInt");
  //    assertEquals(10, si.get("Test").intValue());
  //    MVMap<String, String> ss = s.openMap("stringString");
  //    assertEquals("World", ss.get("Hello"));
  //  }
  //}
  //
  //@Test
  //public void testIterate() {
  //  int size = config.big ? 1000 : 10;
  //  Path file = tempDir.newPath();
  //  Files.deleteIfExists(file);
  //  try (MVStore s = openStore(file)) {
  //    MVMap<Integer, String> m = s.openMap("data", mapBuilder);
  //    Iterator<Integer> it = m.keyIterator(null);
  //    assertFalse(it.hasNext());
  //    for (int i = 0; i < size; i++) {
  //      m.put(i, "hello " + i);
  //    }
  //    s.commit();
  //    it = m.keyIterator(null);
  //    it.next();
  //    assertThrows(UnsupportedOperationException.class, it).remove();
  //
  //    it = m.keyIterator(null);
  //    for (int i = 0; i < size; i++) {
  //      assertTrue(it.hasNext());
  //      assertEquals(i, it.next().intValue());
  //    }
  //    assertFalse(it.hasNext());
  //    assertThrows(NoSuchElementException.class, it).next();
  //    for (int j = 0; j < size; j++) {
  //      it = m.keyIterator(j);
  //      for (int i = j; i < size; i++) {
  //        assertTrue(it.hasNext());
  //        assertEquals(i, it.next().intValue());
  //      }
  //      assertFalse(it.hasNext());
  //    }
  //  }
  //}

  @Test
  public void testIterateReverse() throws IOException {
    //int size = config.big ? 1000 : 10;
    int size = 1000;
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      for (int i = 0; i < size; i++) {
        m.put(i, "hello " + i);
      }
      s.commit();
      Iterator<Integer> it = m.keyIteratorReverse(null);
      it.next();
      Iterator<Integer> finalIt = it;
      assertThatThrownBy(() -> {
        finalIt.remove();
      }).isInstanceOf(UnsupportedOperationException.class);

      it = m.keyIteratorReverse(null);
      for (int i = size - 1; i >= 0; i--) {
        assertTrue(it.hasNext());
        assertEquals(i, it.next().intValue());
      }
      assertFalse(it.hasNext());
      assertThatThrownBy(() -> {
        finalIt.remove();
      }).isInstanceOf(UnsupportedOperationException.class);
      for (int j = 0; j < size; j++) {
        it = m.keyIteratorReverse(j);
        for (int i = j; i >= 0; i--) {
          assertTrue(it.hasNext());
          assertEquals(i, it.next().intValue());
        }
        assertFalse(it.hasNext());
      }
    }
  }

  @Test
  public void testCloseTwice() throws IOException {
    Path file = tempDir.newPath();
    MVStore s = openStore(file);
    MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
    for (int i = 0; i < 3; i++) {
      m.put(i, "hello " + i);
    }
    // closing twice should be fine
    s.close();
    s.close();
  }

  @Test
  public void testSimple() throws IOException {
    Path file = tempDir.newPath();
    try (MVStore s = openStore(file)) {
      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      for (int i = 0; i < 3; i++) {
        m.put(i, "hello " + i);
      }
      s.commit();
      assertEquals("hello 0", m.remove(0));

      assertNull(m.get(0));
      for (int i = 1; i < 3; i++) {
        assertEquals("hello " + i, m.get(i));
      }
    }

    try (MVStore s = openStore(file)) {
      MVMap<Integer, String> m = s.openMap("data", intToStringMapBuilder);
      assertThat(m.get(0)).isNull();
      for (int i = 1; i < 3; i++) {
        assertEquals("hello " + i, m.get(i));
      }
    }
  }

  @Test
  public void testLargerThan2G() throws IOException {
    if (!Boolean.getBoolean("mvstore.slow.test")) {
      return;
    }
    Path file = tempDir.newPath();
    MVStore store = new MVStore.Builder().autoCommitDisabled().compressionLevel(0).cacheSize(16).open(file);
    try {
      MVMap<Integer, byte[]> map = store.openMap("test", IntDataType.INSTANCE, ByteArrayDataType.INSTANCE);
      long last = System.nanoTime();
      byte[] data = new byte[2500];
      new Random(42).nextBytes(data);
      for (int i = 0; ; i++) {
        map.put(i, data);
        if (i % 10000 == 0) {
          store.commit();
          long time = System.nanoTime();
          if (time - last > TimeUnit.SECONDS.toNanos(2)) {
            long mb = store.getFileStore().size() / 1024 / 1024;
            LOG.debug(mb + "/4500");
            if (mb > 4500) {
              break;
            }
            last = time;
          }
        }
      }
      store.commit();
      store.close();
    }
    finally {
      store.closeImmediately();
    }
  }
}