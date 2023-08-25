// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.openapi.util.IntRef;
import com.intellij.openapi.vfs.newvfs.persistent.dev.InvertedFilenameHashBasedIndex.Int2IntMultimap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Implements {@link IntToMultiIntMap} on top of {@link Int2IntMultimap}
 * Thread-safe but not concurrent -- all operations are just guarded by 'this' lock
 */
public final class NonParallelNonPersistentIntToMultiIntMap implements IntToMultiIntMap {
  private final Int2IntMultimap multimap = new Int2IntMultimap();

  @Override
  public synchronized void put(int key,
                               int value) throws IOException {
    multimap.put(adjustKey(key), value);
  }

  @Override
  public synchronized int lookup(int key,
                                 @NotNull ValueAcceptor valuesAcceptor) throws IOException {
    IntRef returnValue = new IntRef(NULL_ID);
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
    IntRef returnValue = new IntRef(NULL_ID);
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
    if (returnValue.get() != NULL_ID) {
      return returnValue.get();
    }

    int newValue = valueCreator.newValueForKey(key);
    multimap.put(adjustedKey, newValue);
    return newValue;
  }

  @Override
  public synchronized void flush() throws IOException {
    //nothing
  }

  @Override
  public synchronized void close() throws IOException {
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
