/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.impl;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-contention counter.
 * Repeated calls to {@link #next()} return numbers which are unique across all calling threads, and which are increasing over calls within one thread.
 */
public class StripedIDGenerator {
  private static final int CHUNK_SIZE = 1000;
  private final AtomicLong nextChunkStart = new AtomicLong();
  // must not be static since we might want to have several instances of this class
  private final ThreadLocal<NextPair> localCounter = new ThreadLocal<NextPair>();
  private static class NextPair {
    long nextId;
    final long limit;

    private NextPair(long nextId, long limit) {
      this.nextId = nextId;
      this.limit = limit;
    }
  }

  public long next() {
    NextPair nextPair = localCounter.get();
    if (nextPair == null || nextPair.nextId == nextPair.limit) {
      long start = nextChunkStart.getAndAdd(CHUNK_SIZE);
      nextPair = new NextPair(start, start + CHUNK_SIZE);
      localCounter.set(nextPair);
    }
    long result = nextPair.nextId;
    nextPair.nextId++;
    return result;
  }
}
