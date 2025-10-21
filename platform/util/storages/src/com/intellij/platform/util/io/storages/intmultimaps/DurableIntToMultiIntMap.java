// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.intmultimaps;

import com.intellij.util.io.CleanableStorage;
import com.intellij.util.io.DataEnumerator;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * Map[int -> int*].
 * This is a building block for {@link com.intellij.util.io.DurableDataEnumerator}, which is why API
 * may look quite specialized.
 * <p>
 * Threading: in general, implementations of this interface must provide at least thread-safety -- e.g.
 * {@link #lookupOrInsert(int, ValueAcceptor, ValueCreator)} expected to be atomic, i.e. {@link ValueCreator#newValueForKey(int)}
 * invoked only once for a key. But the concurrency level is up to the implementation -- it is OK to
 * just have everything guarded by a single lock.
 * <p>
 * Durability is optional: the map implements {@link Closeable} and {@link Flushable}, but it doesn't
 * _require_ to be durable -- empty flush/close methods are OK.
 */
@ApiStatus.Internal
public interface DurableIntToMultiIntMap extends Flushable, Closeable, CleanableStorage {
  int NO_VALUE = DataEnumerator.NULL_ID;

  /**
   * Method <b>adds</b> a value into a set of values for the key.
   * BEWARE: it is multi-value map -- new values do not overwrite previous ones, but appended to the set of values for the key.
   * To overwrite previous value: remove and add new one, or use {@link #replace(int, int, int)}  method
   *
   * @return true if (key,value) pair was really put into the map -- i.e., wasn't there before
   */
  boolean put(int key,
              int value) throws IOException;

  /**
   * if mapping (key, oldValue) exists -- replaces it with (key, newValue), otherwise do nothing
   *
   * @return true if actually replaced something, i.e. there was (key, oldValue) mapping before, false if there was no
   * such mapping, and hence nothing was replaced
   */
  boolean replace(int key,
                  int oldValue,
                  int newValue) throws IOException;

  boolean has(int key,
              int value) throws IOException;

  /**
   * Method lookups values for a key, and gets them tested by valuesAcceptor -- and return the first value
   * accepted by valuesAcceptor. If no values were found, or none were accepted -- returns {@link #NO_VALUE}.
   *
   * @return first value for a key which was accepted by valuesProcessor -- or {@link #NO_VALUE} if no
   * values were found, or none of values found were accepted by valuesAcceptor
   */
  int lookup(int key,
             @NotNull ValueAcceptor valuesAcceptor) throws IOException;

  /**
   * Method behaves the same way as {@link #lookup(int, ValueAcceptor)}, but if no values were found/none were
   * accepted -- method calls {@link ValueCreator#newValueForKey(int)}, inserts returned value into the map,
   * and returns it. Method never return {@link #NO_VALUE}.
   *
   * @return value for a key which was accepted by valuesProcessor. If no values were found,
   * {@link ValueCreator#newValueForKey(int)} is called, and newly generated value inserted into the map,
   * and returned. Method should never return {@link #NO_VALUE}
   */
  int lookupOrInsert(int key,
                     @NotNull ValueAcceptor valuesAcceptor,
                     @NotNull ValueCreator valueCreator) throws IOException;

  /**
   * Removes (key,value) mapping from the multimap.
   *
   * @return true if such mapping existed and was removed, false if there wasn't such a mapping (i.e. nothing changed)
   */
  boolean remove(int key,
                 int value) throws IOException;

  int size() throws IOException;

  boolean isEmpty() throws IOException;

  /**
   * Scans through all the records, and supply (key,value) pairs to processor. Stops iteration if
   * processor returns false.
   * @return true if scanned through all the records, false if iteration terminates
   * prematurely because processor returns false
   */
  boolean forEach(@NotNull KeyValueProcessor processor) throws IOException;

  void clear() throws IOException;

  boolean isClosed();

  @FunctionalInterface
  interface ValueAcceptor {
    boolean accept(int value) throws IOException;
  }


  @FunctionalInterface
  interface ValueCreator {
    /** Method should never return {@link #NO_VALUE} */
    int newValueForKey(int key) throws IOException;
  }

  @FunctionalInterface
  interface KeyValueProcessor {
    boolean process(int key,
                    int value) throws IOException;
  }
}
