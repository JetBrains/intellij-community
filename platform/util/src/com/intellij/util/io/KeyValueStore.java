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

  boolean isDirty();
}
