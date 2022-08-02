// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.io;

import org.jetbrains.annotations.ApiStatus;

import java.io.Closeable;
import java.io.IOException;

@ApiStatus.Experimental
public interface KeyValueStore<K, V> extends Closeable {
  V get(K key) throws IOException;

  void put(K key, V value) throws IOException;

  void force();

  /**
   * Method was introduced for analytics purposes, and for that it is not required to be precise,
   * i.e. it is OK to provide estimations, outdated info, include keys just removed, or something like
   * that -- but it should be fast (ideally O(1), but at least sublinear on size).
   *
   * It could be hard/costly to implement this method precisely for data structures with layered caching,
   * and it is not clear would the method be useful in other contexts there precision is important,
   * is it worth to define it as precise, and take associated costs.
   *
   * @return approximated number of keys in index, or -1 if this index doesn't provide such information
   */
  @ApiStatus.Experimental
  int keysCountApproximately();
}
