// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap;

import com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMap;
import com.intellij.platform.util.io.storages.intmultimaps.DurableIntToMultiIntMapTestBase;
import com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMapTestBase;
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory;
import com.intellij.util.io.ClosedStorageException;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.platform.util.io.storages.intmultimaps.IntToMultiLongMap.NO_VALUE;
import static com.intellij.platform.util.io.storages.intmultimaps.extendiblehashmap.ExtendibleHashMapInt32ToInt64.DEFAULT_SEGMENT_SIZE;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtendibleHashMapInt32ToInt64Test extends DurableIntToMultiIntMapTestBase<ExtendibleHashMapInt32ToInt64Test.Int32View> {
  private static final int ENTRIES_TO_FORCE_SPLITS = 4_000;
  private static final int MAP_HEADER_SIZE = 76;

  @TempDir Path tempDir;

  ExtendibleHashMapInt32ToInt64Test() {
    super(/*entriesCountToTest: */4_000_000);
  }

  @Test
  void fullSegmentTableRejectsFurtherExpansion() throws IOException {
    var storage = new TestStorage();
    var map = new ExtendibleHashMapInt32ToInt64(storage);
    try {
      var failure = assertThrows(IllegalStateException.class, () -> {
        for (int key = 1; key <= 100_000; key++) {
          map.put(key, key);
        }
      }, "The map must stop when 512 table slots cannot be doubled");
      assertTrue(failure.getMessage().contains("currentSize=512"), "The table must use all 512 slots before expansion fails");
      assertTrue(Short.toUnsignedInt(storage.segmentsTable().get(JAVA_SHORT, 1022)) > 0,
                 "The last table slot must hold a segment index");
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void mappedStorageKeepsHeaderSeparateFromSegmentsTable(@TempDir @NotNull Path tempDir) throws IOException {
    var path = tempDir.resolve("segments.map");
    var factory = MMappedFileStorageFactory.withDefaults().pageSize(4096);
    try (var mappedStorage = factory.open(tempDir.resolve("invalid-header.map"))) {
      assertThrows(IllegalArgumentException.class, () -> {
        try (var invalid = new ExtendibleHashMapStorageOverMMappedFile(mappedStorage, 1025, 1024)) {
          assertEquals(1025, invalid.headerSize());
        }
      });
    }
    try (var storage = new ExtendibleHashMapStorageOverMMappedFile(factory.open(path), MAP_HEADER_SIZE, 1024)) {
      assertTrue(storage.isEmpty());
      assertThrows(IllegalStateException.class, storage::segmentsTable);
      assertThrows(IllegalStateException.class, () -> storage.allocateSegment(0));
      var header = storage.header();
      var table = storage.segmentsTable();
      assertEquals(MAP_HEADER_SIZE, storage.headerSize());
      assertEquals(MAP_HEADER_SIZE, header.byteSize());
      assertEquals(1024, table.byteSize());
      header.set(JAVA_INT, 0, 42);
      table.set(JAVA_INT, 0, 73);
      assertEquals(42, storage.header().get(JAVA_INT, 0));
      storage.flush();
    }
    try (var reopened = new ExtendibleHashMapStorageOverMMappedFile(factory.open(path), MAP_HEADER_SIZE, 1024)) {
      assertEquals(42, reopened.header().get(JAVA_INT, 0));
      assertEquals(73, reopened.segmentsTable().get(JAVA_INT, 0));
      reopened.clear();
      assertEquals(0, reopened.header().get(JAVA_INT, 0));
    }
  }

  @Override
  protected Int32View openInDir(@NotNull Path tempDir) throws IOException {
    return new Int32View(open(tempDir.resolve("map.map")));
  }

  @Test
  void storesValuesOutsideIntRange(@TempDir @NotNull Path tempDir) throws IOException {
    var storagePath = tempDir.resolve("map.map");
    var map = open(storagePath);
    try {
      long firstValue = (long)Integer.MAX_VALUE + 1;
      long secondValue = Long.MAX_VALUE;

      assertTrue(map.put(42, firstValue));
      assertTrue(map.put(42, secondValue));
      assertFalse(map.put(42, firstValue));
      assertEquals(firstValue, map.lookup(42, value -> value == firstValue));
      assertEquals(secondValue, map.lookup(42, value -> value == secondValue));

      assertTrue(map.replace(42, firstValue, Long.MIN_VALUE));
      assertFalse(map.has(42, firstValue));
      assertTrue(map.has(42, Long.MIN_VALUE));
      assertEquals(2, map.size());
      assertTrue(map.replace(42, Long.MIN_VALUE, secondValue));
      assertEquals(1, map.size());
      assertTrue(map.remove(42, secondValue));
      assertTrue(map.isEmpty());
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void lookupAndModifySupportsAllValueTransitions(@TempDir @NotNull Path tempDir) throws Exception {
    var map = open(tempDir.resolve("map.map"));
    try {
      IntToMultiLongMapTestBase.assertLookupAndModifyContract(map);
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void lookupAndModifyAppliesAllChangesBeforeProcessorStops(@TempDir @NotNull Path tempDir) throws Exception {
    var map = open(tempDir.resolve("map.map"));
    try {
      IntToMultiLongMapTestBase.assertLookupAndModifyAppliesAllChangesBeforeProcessorStops(map);
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void lookupAndModifyKeepsCompletedChangesWhenProcessorThrows(@TempDir @NotNull Path tempDir) throws Exception {
    var storagePath = tempDir.resolve("map.map");
    var map = open(storagePath);
    var firstValue = new long[]{NO_VALUE};
    try {
      map.put(1, 10);
      map.put(1, 20);

      var mapToModify = map;
      assertThrows(IOException.class, () -> mapToModify.lookupAndModify(1, (oldValue, newValueRef) -> {
        if (firstValue[0] == NO_VALUE) {
          firstValue[0] = oldValue;
          newValueRef.set(NO_VALUE);
          return true;
        }
        throw new IOException("Test exception");
      }));

      assertEquals(NO_VALUE, map.lookup(1, value -> value == firstValue[0]));
      assertEquals(1, map.size());

      map.close();
      map = open(storagePath);
      assertEquals(NO_VALUE, map.lookup(1, value -> value == firstValue[0]));
      assertEquals(1, map.size());
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void lookupAndModifyKeepsSizeAfterSegmentSplits() throws IOException {
    var storagePath = tempDir.resolve("map.map");
    var map = open(storagePath);
    try {
      for (int i = 1; i <= ENTRIES_TO_FORCE_SPLITS; i++) {
        long value = valueForKey(i);
        assertTrue(map.lookupAndModify(i, (oldValue, newValueRef) -> {
          if (oldValue == NO_VALUE) {
            newValueRef.set(value);
          }
          return true;
        }));
        assertEquals(i, map.size(), "Each inserted mapping must increase the size");
      }

      map.close();
      map = open(storagePath);
      assertEquals(ENTRIES_TO_FORCE_SPLITS, map.size(), "The size must remain correct after reopening");
      for (int key = 1; key <= ENTRIES_TO_FORCE_SPLITS; key++) {
        long expectedValue = valueForKey(key);
        assertEquals(expectedValue, map.lookup(key, value -> value == expectedValue));
      }
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void operationsThatRequireOpenStorageRejectClosedMap(@TempDir @NotNull Path tempDir) throws Exception {
    var map = open(tempDir.resolve("map.map"));
    try {
      map.close();

      assertThrows(ClosedStorageException.class, () -> map.put(1, 1));
      assertThrows(ClosedStorageException.class, () -> map.has(1, 1));
      assertThrows(ClosedStorageException.class, () -> map.lookup(1, _ -> false));
      assertThrows(ClosedStorageException.class, () -> map.lookupAndModify(1, (_, _) -> false));
      assertThrows(ClosedStorageException.class, () -> map.remove(1, 1));
      assertThrows(ClosedStorageException.class, () -> map.replace(1, 1, 2));
      assertThrows(ClosedStorageException.class, () -> map.forEach((_, _) -> true));
      assertThrows(ClosedStorageException.class, map::clear);
      assertThrows(ClosedStorageException.class, map::flush);
      assertThrows(ClosedStorageException.class, map::markDirty);
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void valuesSurviveSegmentSplitsAndReopen(@TempDir @NotNull Path tempDir) throws IOException {
    var storagePath = tempDir.resolve("map.map");
    var map = open(storagePath);
    try {
      for (int key = 1; key <= ENTRIES_TO_FORCE_SPLITS; key++) {
        assertTrue(map.put(key, valueForKey(key)));
      }
      assertEquals(ENTRIES_TO_FORCE_SPLITS, map.size());

      map.close();
      map = open(storagePath);
      assertTrue(map.wasProperlyClosed());

      for (int key = 1; key <= ENTRIES_TO_FORCE_SPLITS; key++) {
        long expectedValue = valueForKey(key);
        assertEquals(expectedValue, map.lookup(key, value -> value == expectedValue));
      }
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void zeroValueIsRejected(@TempDir @NotNull Path tempDir) throws IOException {
    var map = open(tempDir.resolve("map.map"));
    try {
      assertThrows(IllegalArgumentException.class, () -> map.put(1, 0));
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void removeReplaceAndClearMarkMapAsDirty() throws IOException {
    var storage = new TestStorage();
    var map = new ExtendibleHashMapInt32ToInt64(storage);
    try {
      map.put(1, 10);
      map.flush();

      assertTrue(map.remove(1, 10));
      assertTrue(map.isDirty());
      assertEquals(TestStorage.FILE_STATUS_OPENED, storage.fileStatus());
      map.flush();

      map.put(1, 10);
      map.flush();
      assertTrue(map.replace(1, 10, 20));
      assertTrue(map.isDirty());
      assertEquals(TestStorage.FILE_STATUS_OPENED, storage.fileStatus());
      map.flush();

      map.clear();
      assertTrue(map.isDirty());
      assertEquals(TestStorage.FILE_STATUS_OPENED, storage.fileStatus());
    }
    finally {
      map.closeAndClean();
    }
  }

  @Test
  void failedFlushKeepsMapDirtyAndOpened() throws IOException {
    var storage = new TestStorage();
    var map = new ExtendibleHashMapInt32ToInt64(storage);
    try {
      map.put(1, 10);
      storage.failFlush = true;

      assertThrows(IOException.class, map::flush);
      assertTrue(map.isDirty());
      assertEquals(TestStorage.FILE_STATUS_OPENED, storage.fileStatus());
      assertEquals(1, storage.flushCount);

      storage.failFlush = false;
      map.flush();
      assertFalse(map.isDirty());
      assertEquals(TestStorage.FILE_STATUS_PROPERLY_CLOSED, storage.fileStatus());
      assertEquals(2, storage.flushCount);
    }
    finally {
      storage.failFlush = false;
      map.closeAndClean();
    }
  }

  @Test
  void closeFlushesStorage() throws IOException {
    var storage = new TestStorage();
    var map = new ExtendibleHashMapInt32ToInt64(storage);
    try {
      map.put(1, 10);
      map.close();

      assertEquals(1, storage.flushCount);
      assertEquals(TestStorage.FILE_STATUS_PROPERLY_CLOSED, storage.fileStatusAtClose);
      assertFalse(storage.isOpen());
    }
    finally {
      map.closeAndClean();
    }
  }

  private static long valueForKey(int key) {
    return ((long)key << Integer.SIZE) | Integer.toUnsignedLong(~key);
  }

  private static @NotNull ExtendibleHashMapInt32ToInt64 open(@NotNull Path storagePath) throws IOException {
    return MMappedFileStorageFactory.withDefaults()
      .pageSize(DEFAULT_SEGMENT_SIZE * 32)
      .wrapStorageSafely(
        storagePath,
        mappedStorage -> new ExtendibleHashMapInt32ToInt64(
          new ExtendibleHashMapStorageOverMMappedFile(mappedStorage, MAP_HEADER_SIZE, DEFAULT_SEGMENT_SIZE)
        )
      );
  }

  static final class Int32View implements DurableIntToMultiIntMap {
    private final ExtendibleHashMapInt32ToInt64 delegate;

    private Int32View(@NotNull ExtendibleHashMapInt32ToInt64 delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean put(int key, int value) throws IOException {
      return delegate.put(key, value);
    }

    @Override
    public boolean replace(int key, int oldValue, int newValue) throws IOException {
      return delegate.replace(key, oldValue, newValue);
    }

    @Override
    public boolean has(int key, int value) throws IOException {
      return delegate.has(key, value);
    }

    @Override
    public int lookup(int key, @NotNull ValueAcceptor valuesAcceptor) throws IOException {
      return Math.toIntExact(delegate.lookup(key, value -> valuesAcceptor.accept(Math.toIntExact(value))));
    }

    @Override
    public int lookupOrInsert(int key,
                              @NotNull ValueAcceptor valuesAcceptor,
                              @NotNull ValueCreator valueCreator) throws IOException {
      int value = lookup(key, valuesAcceptor);
      if (value != NO_VALUE) {
        return value;
      }

      int newValue = valueCreator.newValueForKey(key);
      boolean inserted = put(key, newValue);
      assert inserted : "The new value must not exist";
      return newValue;
    }

    @Override
    public boolean remove(int key, int value) throws IOException {
      return delegate.remove(key, value);
    }

    @Override
    public int size() {
      return delegate.size();
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public boolean forEach(@NotNull KeyValueProcessor processor) throws IOException {
      return delegate.forEach((key, value) -> processor.process(key, Math.toIntExact(value)));
    }

    @Override
    public void clear() throws IOException {
      delegate.clear();
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }

    @Override
    public void closeAndClean() throws IOException {
      delegate.closeAndClean();
    }

    @Override
    public boolean isClosed() {
      return delegate.isClosed();
    }
  }

  private static final class TestStorage implements ExtendibleHashMapStorage {
    private static final int SEGMENT_SIZE = 1024;
    private static final long FILE_STATUS_OFFSET = Integer.BYTES * 3L + Byte.BYTES;
    private static final byte FILE_STATUS_OPENED = 0;
    private static final byte FILE_STATUS_PROPERLY_CLOSED = 1;

    private final Arena arena = Arena.ofConfined();
    private final List<MemorySegment> segments = new ArrayList<>();
    private MemorySegment header;
    private MemorySegment segmentsTable;

    private boolean open = true;
    private boolean failFlush;
    private int flushCount;
    private byte fileStatusAtClose;

    @Override
    public boolean isOpen() {
      return open;
    }

    @Override
    public boolean isEmpty() {
      return header == null && segmentsTable == null && segments.isEmpty();
    }

    @Override
    public int segmentSize() {
      return SEGMENT_SIZE;
    }

    @Override
    public int headerSize() {
      return MAP_HEADER_SIZE;
    }

    @Override
    public @NotNull MemorySegment header() {
      if (header == null) {
        if (!segments.isEmpty()) {
          throw new IllegalStateException("Segments exist without a header");
        }
        header = arena.allocate(headerSize(), Integer.BYTES);
      }
      return header;
    }

    @Override
    public @NotNull MemorySegment segmentsTable() {
      if (header == null) {
        throw new IllegalStateException("Call header() first");
      }
      if (segmentsTable == null) {
        segmentsTable = arena.allocate(SEGMENT_SIZE, Long.BYTES);
      }
      return segmentsTable;
    }

    @Override
    public @NotNull MemorySegment segment(int segmentIndex) {
      return segments.get(segmentIndex);
    }

    @Override
    public @NotNull MemorySegment allocateSegment(int segmentIndex) {
      if (segmentIndex != segments.size()) {
        throw new IllegalArgumentException("The segment index must be " + segments.size() + ", but it is " + segmentIndex);
      }
      var segment = arena.allocate(SEGMENT_SIZE, Long.BYTES);
      segments.add(segment);
      return segment;
    }

    @Override
    public void clear() {
      header = null;
      segmentsTable = null;
      segments.clear();
    }

    @Override
    public void flush() throws IOException {
      flushCount++;
      if (failFlush) {
        throw new IOException("Test flush failure");
      }
    }

    @Override
    public void close() {
      if (open) {
        fileStatusAtClose = fileStatus();
        open = false;
        arena.close();
      }
    }

    @Override
    public void closeAndClean() {
      close();
      segments.clear();
      header = null;
      segmentsTable = null;
    }

    private byte fileStatus() {
      return header.get(JAVA_BYTE, FILE_STATUS_OFFSET);
    }
  }
}
