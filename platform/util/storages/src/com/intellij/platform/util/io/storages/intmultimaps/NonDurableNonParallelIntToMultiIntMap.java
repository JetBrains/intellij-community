// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import com.intellij.openapi.util.IntRef;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Implements {@link DurableIntToMultiIntMap} on top of {@link Int2IntMultimap}
 * <b>Not durable</b>: keeps content in memory only, lost on restart.
 * <b>Thread-safe but not concurrent</b> -- all operations are just guarded by 'this' lock
 */
@ApiStatus.Internal
public final class NonDurableNonParallelIntToMultiIntMap implements DurableIntToMultiIntMap {
  private final Int2IntMultimap multimap;

  public NonDurableNonParallelIntToMultiIntMap() {
    multimap = new Int2IntMultimap();
  }

  public NonDurableNonParallelIntToMultiIntMap(int capacity,
                                               float loadFactor) {
    multimap = new Int2IntMultimap(capacity, loadFactor);
  }

  @Override
  public synchronized boolean put(int key,
                                  int value) throws IOException {
    //return multimap.put(adjustKey(key), value);
    return multimap.put(key, value);
  }

  @Override
  public synchronized boolean replace(int key,
                                      int oldValue,
                                      int newValue) throws IOException {
    return multimap.replace(key, oldValue, newValue);
  }

  @Override
  public synchronized boolean has(int key,
                                  int value) throws IOException {
    return multimap.has(key, value);
  }

  @Override
  public synchronized int lookup(int key,
                                 @NotNull ValueAcceptor valuesAcceptor) throws IOException {
    IntRef returnValue = new IntRef(NO_VALUE);
    multimap.lookup(key, value -> {
      try {
        if (valuesAcceptor.accept(value)) {
          returnValue.set(value);
          return false;
        }
        return true;
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
    return returnValue.get();
  }

  @Override
  public synchronized int lookupOrInsert(int key,
                                         @NotNull ValueAcceptor valuesAcceptor,
                                         @NotNull ValueCreator valueCreator) throws IOException {
    IntRef returnValue = new IntRef(NO_VALUE);
    multimap.lookup(key, value -> {
      try {
        if (valuesAcceptor.accept(value)) {
          returnValue.set(value);
          return false;
        }
        return true;
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
    if (returnValue.get() != NO_VALUE) {
      return returnValue.get();
    }

    int newValue = valueCreator.newValueForKey(key);
    multimap.put(key, newValue);
    return newValue;
  }

  @Override
  public synchronized boolean remove(int key, int value) throws IOException {
    return multimap.remove(key, value);
  }

  @Override
  public synchronized int size() {
    return multimap.size();
  }

  @Override
  public synchronized boolean isEmpty() throws IOException {
    return multimap.size() == 0;
  }

  @Override
  public synchronized boolean forEach(@NotNull KeyValueProcessor processor) {
    return multimap.forEach((key, value) -> {
      try {
        return processor.process(key, value);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  @Override
  public synchronized void clear() throws IOException {
    multimap.clear();
  }

  @Override
  public synchronized boolean isClosed() {
    return false;
  }

  @Override
  public synchronized void flush() throws IOException {
    //nothing
  }

  @Override
  public synchronized void close() throws IOException {
    //nothing
  }

  @Override
  public synchronized void closeAndClean() throws IOException {
    //nothing
  }
}
