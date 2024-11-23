// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.lang;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@ApiStatus.Internal
public final class DirectByteBufferPool {
  public static final DirectByteBufferPool DEFAULT_POOL = new DirectByteBufferPool(it -> {});

  private static final int MIN_SIZE = 2048;
  private static final int MAX_POOL_SIZE = 32;

  private final ConcurrentSkipListMap<Integer, ByteBuffer> pool = new ConcurrentSkipListMap<>();
  private final AtomicInteger count = new AtomicInteger();
  private final @NotNull Consumer<? super ByteBuffer> releaser;

  // ByteBufferCleaner cannot be located in this module (JDK 9 at least is required)
  public DirectByteBufferPool(@NotNull Consumer<? super ByteBuffer> releaser) {
    this.releaser = releaser;
  }

  public @NotNull ByteBuffer allocate(int requiredSize) {
    int size = roundUpInt(requiredSize, MIN_SIZE);
    Map.Entry<Integer, ByteBuffer> entry;
    do {
      entry = pool.ceilingEntry(size);
      if (entry == null) {
        break;
      }
    }
    while (!pool.remove(entry.getKey(), entry.getValue()));

    ByteBuffer result;
    if (entry == null) {
      result = ByteBuffer.allocateDirect(size);
    }
    else {
      count.decrementAndGet();
      result = entry.getValue();
    }

    result.limit(requiredSize);
    return result;
  }

  public void release(@NotNull ByteBuffer buffer) {
    if (buffer.isReadOnly()) {
      // slice of mapped byte buffer
      return;
    }

    // limit is set on allocate
    buffer.rewind();
    buffer.order(ByteOrder.BIG_ENDIAN);
    // keep the only buffer for size
    if (count.get() < MAX_POOL_SIZE && pool.putIfAbsent(buffer.capacity(), buffer) == null) {
      count.incrementAndGet();
    }
    else {
      this.releaser.accept(buffer);
    }
  }

  private static int roundUpInt(int x, @SuppressWarnings("SameParameterValue") int blockSizePowerOf2) {
    return (x + blockSizePowerOf2 - 1) & (-blockSizePowerOf2);
  }
}
