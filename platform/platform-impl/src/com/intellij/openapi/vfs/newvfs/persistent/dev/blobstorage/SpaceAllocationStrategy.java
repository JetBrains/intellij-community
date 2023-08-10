// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.blobstorage;

/**
 * Allocation strategy for records in {@link StreamlinedBlobStorage}: "how much capacity reserve for a
 * record of (current) size X?"
 */
public interface SpaceAllocationStrategy {
  /**
   * @return how long buffers create for a new record (i.e. in {@link StreamlinedBlobStorage#writeToRecord(int, ByteBufferWriter)}
   * there recordId=NULL_ID)
   */
  int defaultCapacity();

  /**
   * @return if a writer in a {@link StreamlinedBlobStorage#writeToRecord(int, ByteBufferWriter)}
   * returns buffer of (length, capacity) -- how big record to allocate for the data? Buffer actual size (limit-position)
   * and buffer.capacity is considered. returned value must be >= actualLength
   */
  int capacity(final int actualLength,
               final int currentCapacity);

  class WriterDecidesStrategy implements SpaceAllocationStrategy {
    private final int defaultCapacity;

    public WriterDecidesStrategy(final int defaultCapacity) {
      if (defaultCapacity <= 0 || defaultCapacity >= StreamlinedBlobStorageOverLockFreePagesStorage.MAX_CAPACITY) {
        throw new IllegalArgumentException(
          "defaultCapacity(" +
          defaultCapacity +
          ") must be in [1," +
          StreamlinedBlobStorageOverLockFreePagesStorage.MAX_CAPACITY +
          "]");
      }
      this.defaultCapacity = defaultCapacity;
    }

    @Override
    public int defaultCapacity() {
      return defaultCapacity;
    }

    @Override
    public int capacity(final int actualLength,
                        final int currentCapacity) {
      if (actualLength < 0) {
        throw new IllegalArgumentException("actualLength(=" + actualLength + " should be >=0");
      }
      if (currentCapacity < actualLength) {
        throw new IllegalArgumentException("currentCapacity(=" + currentCapacity + ") should be >= actualLength(=" + actualLength + ")");
      }
      return currentCapacity;
    }

    @Override
    public String toString() {
      return "WriterDecidesStrategy{default: " + defaultCapacity + '}';
    }
  }

  class DataLengthPlusFixedPercentStrategy implements SpaceAllocationStrategy {
    private final int defaultCapacity;
    private final int minCapacity;
    private final int percentOnTheTop;

    public DataLengthPlusFixedPercentStrategy(final int defaultCapacity,
                                              final int minCapacity,
                                              final int percentOnTheTop) {
      if (defaultCapacity <= 0 || defaultCapacity > StreamlinedBlobStorageOverLockFreePagesStorage.MAX_CAPACITY) {
        throw new IllegalArgumentException(
          "defaultCapacity(" +
          defaultCapacity +
          ") must be in [1," +
          StreamlinedBlobStorageOverLockFreePagesStorage.MAX_CAPACITY +
          "]");
      }
      if (minCapacity <= 0 || minCapacity > defaultCapacity) {
        throw new IllegalArgumentException("minCapacity(" + minCapacity + ") must be > 0 && <= defaultCapacity(" + defaultCapacity + ")");
      }
      if (percentOnTheTop < 0) {
        throw new IllegalArgumentException("percentOnTheTop(" + percentOnTheTop + ") must be >= 0");
      }
      this.defaultCapacity = defaultCapacity;
      this.minCapacity = minCapacity;
      this.percentOnTheTop = percentOnTheTop;
    }

    @Override
    public int defaultCapacity() {
      return defaultCapacity;
    }

    @Override
    public int capacity(final int actualLength,
                        final int currentCapacity) {
      if (actualLength < 0) {
        throw new IllegalArgumentException("actualLength(=" + actualLength + " should be >=0");
      }
      if (currentCapacity < actualLength) {
        throw new IllegalArgumentException("currentCapacity(=" + currentCapacity + ") should be >= actualLength(=" + actualLength + ")");
      }
      final double capacity = Math.ceil(actualLength * (1.0 + percentOnTheTop / 100.0));
      final int advisedCapacity = (int)Math.max(minCapacity, capacity);
      if (advisedCapacity < 0 || advisedCapacity > StreamlinedBlobStorageOverLockFreePagesStorage.MAX_CAPACITY) {
        return StreamlinedBlobStorageOverLockFreePagesStorage.MAX_CAPACITY;
      }
      return advisedCapacity;
    }

    @Override
    public String toString() {
      return "DataLengthPlusFixedPercentStrategy{" +
             "length + " + percentOnTheTop + "%" +
             ", min: " + minCapacity +
             ", default: " + defaultCapacity + "}";
    }
  }
}
