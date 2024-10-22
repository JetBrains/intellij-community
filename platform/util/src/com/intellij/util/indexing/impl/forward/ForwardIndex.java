// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.forward;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.KeyValueStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Represents key-value storage held by <a href="https://en.wikipedia.org/wiki/Search_engine_indexing#The_forward_index">forward index data structure</a>.
 * Thread-safety is up to implementation
 */
public interface ForwardIndex extends KeyValueStore<Integer, ByteArraySequence> {
  @Nullable
  @Override
  ByteArraySequence get(@NotNull Integer key) throws IOException;

  @Override
  void put(@NotNull Integer key, @Nullable ByteArraySequence value) throws IOException;

  void clear() throws IOException;

  //MAYBE RC: push it up to KeyValueStore?
  boolean isClosed();
}