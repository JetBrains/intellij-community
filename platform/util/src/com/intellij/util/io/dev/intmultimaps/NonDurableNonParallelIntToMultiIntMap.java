// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.dev.intmultimaps;

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
  private final Int2IntMultimap multimap = new Int2IntMultimap();

  @Override
  public synchronized boolean put(int key,
                                  int value) throws IOException {
    return multimap.put(adjustKey(key), value);
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
    multimap.lookup(adjustKey(key), value -> {
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
    int adjustedKey = adjustKey(key);
    multimap.lookup(adjustedKey, value -> {
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
    multimap.put(adjustedKey, newValue);
    return newValue;
  }

  @Override
  public boolean remove(int key, int value) throws IOException {
    return multimap.remove(key, value);
  }

  @Override
  public synchronized int size() {
    return multimap.size();
  }

  @Override
  public boolean isEmpty() throws IOException {
    return multimap.size() == 0;
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
  public void closeAndClean() throws IOException {
    //nothing
  }

  private static int adjustKey(int key) {
    if (key == Int2IntMultimap.NO_VALUE) {
      //Int2IntMultimap doesn't allow 0 keys/values, hence replace 0 key with just any value!=0. Key doesn't
      // identify value uniquely anyway, hence this replacement just adds another collision -- basically,
      // we replaced original Key.hash with our own hash, which avoids 0 at the cost of slightly higher collision
      // chances
      return -1;// any value!=0 will do
    }
    return key;
  }
}
