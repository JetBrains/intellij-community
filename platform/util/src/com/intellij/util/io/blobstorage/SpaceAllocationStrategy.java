// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.blobstorage;

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

  final class WriterDecidesStrategy implements SpaceAllocationStrategy {
    private final int defaultCapacity;
    private final int maxCapacity;

    public WriterDecidesStrategy(final int maxCapacity,
                                 final int defaultCapacity) {
      if (maxCapacity <= 0) {
        throw new IllegalArgumentException("maxCapacity(" + maxCapacity + ") must be >0");
      }
      if (defaultCapacity <= 0 || defaultCapacity >= maxCapacity) {
        throw new IllegalArgumentException("defaultCapacity(" + defaultCapacity + ") must be in [1," + maxCapacity + "]");
      }

      this.maxCapacity = maxCapacity;
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
        throw new IllegalArgumentException("actualLength(=" + actualLength + " must be >=0");
      }
      if (currentCapacity < actualLength) {
        throw new IllegalArgumentException("currentCapacity(=" + currentCapacity + ") must be >= actualLength(=" + actualLength + ")");
      }
      if (currentCapacity > maxCapacity) {
        throw new IllegalArgumentException("currentCapacity(=" + currentCapacity + ") must be <= max(=" + maxCapacity + ")");
      }
      return currentCapacity;
    }

    @Override
    public String toString() {
      return "WriterDecidesStrategy{default: " + defaultCapacity + ", max: " + maxCapacity + "}";
    }
  }

  final class DataLengthPlusFixedPercentStrategy implements SpaceAllocationStrategy {
    private final int defaultCapacity;
    private final int minCapacity;
    private final int maxCapacity;
    private final int percentOnTheTop;

    public DataLengthPlusFixedPercentStrategy(final int minCapacity,
                                              final int defaultCapacity,
                                              final int maxCapacity,
                                              final int percentOnTheTop) {
      if (maxCapacity <= 0) {
        throw new IllegalArgumentException("maxCapacity(" + maxCapacity + ") must be >0");
      }
      if (defaultCapacity <= 0 || defaultCapacity > maxCapacity) {
        throw new IllegalArgumentException("defaultCapacity(" + defaultCapacity + ") must be in [1," + maxCapacity + "]");
      }
      if (minCapacity <= 0 || minCapacity > defaultCapacity) {
        throw new IllegalArgumentException("minCapacity(" + minCapacity + ") must be > 0 && <= defaultCapacity(" + defaultCapacity + ")");
      }
      if (percentOnTheTop < 0) {
        throw new IllegalArgumentException("percentOnTheTop(" + percentOnTheTop + ") must be >= 0");
      }

      this.minCapacity = minCapacity;
      this.defaultCapacity = defaultCapacity;
      this.maxCapacity = maxCapacity;

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
      if (advisedCapacity < 0 || advisedCapacity > maxCapacity) {
        return maxCapacity;
      }
      return advisedCapacity;
    }

    @Override
    public String toString() {
      return "DataLengthPlusFixedPercentStrategy{" +
             "length + " + percentOnTheTop + "%" +
             ", min: " + minCapacity +
             ", max: " + maxCapacity +
             ", default: " + defaultCapacity + "}";
    }
  }
}
