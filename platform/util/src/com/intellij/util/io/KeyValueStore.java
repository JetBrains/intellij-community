// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io;

import com.intellij.openapi.Forceable;
import org.jetbrains.annotations.ApiStatus;

import java.io.Closeable;
import java.io.IOException;

@ApiStatus.Experimental
public interface KeyValueStore<K, V> extends Closeable, Forceable {
  V get(K key) throws IOException;

  void put(K key, V value) throws IOException;

  @Override
  void force() throws IOException;

  @Override
  boolean isDirty();
}
