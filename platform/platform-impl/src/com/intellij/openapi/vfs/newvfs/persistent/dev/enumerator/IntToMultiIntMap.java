// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.dev.enumerator;

import com.intellij.util.io.DataEnumeratorEx;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * Map[int -> int*].
 * This is a building block for {@link DurableEnumerator}, which is why API may look quite specialized.
 * <p>
 * Threading: in general, implementations of this interface must provide at least thread-safety -- e.g.
 * {@link #lookupOrInsert(int, ValueAcceptor, ValueCreator)} expected to be atomic, i.e. {@link ValueCreator#newValueForKey(int)}
 * invoked only once for a key. But the concurrency level is up to the implementation -- it is OK to
 * just have everything guarded by a single lock.
 * <p>
 * Durability is optional: the map implements {@link Closeable} and {@link Flushable}, but it doesn't
 * _require_ to be durable -- empty flush/close methods are OK.
 */
public interface IntToMultiIntMap extends Flushable, Closeable {
  int NULL_ID = DataEnumeratorEx.NULL_ID;

  void put(int key,
           int value) throws IOException;

  /**
   * Method lookups values for a key, and gets them tested by valuesAcceptor -- and return the first value
   * accepted by valuesAcceptor. If no values were found, or none were accepted -- returns {@link #NULL_ID}.
   *
   * @return first value for a key which was accepted by valuesProcessor -- or {@link #NULL_ID} if no
   * values were found, or none of values found were accepted by valuesAcceptor
   */
  int lookup(int key,
             @NotNull ValueAcceptor valuesAcceptor) throws IOException;

  /**
   * Method behaves the same way as {@link #lookup(int, ValueAcceptor)}, but if no values were found/none were
   * accepted -- method calls {@link ValueCreator#newValueForKey(int)}, inserts returned value into the map,
   * and returns it. Method never return {@link #NULL_ID}.
   *
   * @return value for a key which was accepted by valuesProcessor. If no values were found,
   * {@link ValueCreator#newValueForKey(int)} is called, and newly generated value inserted into the map,
   * and returned. Method should never return {@link #NULL_ID}
   */
  int lookupOrInsert(int key,
                     @NotNull ValueAcceptor valuesAcceptor,
                     @NotNull ValueCreator valueCreator) throws IOException;


  @FunctionalInterface
  interface ValueAcceptor {
    boolean accept(int value) throws IOException;
  }


  @FunctionalInterface
  interface ValueCreator {
    /** Method should never return {@link #NULL_ID} */
    int newValueForKey(int key) throws IOException;
  }
}
