/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.indexing.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.util.Map;

/**
 * Represents a <a href="https://en.wikipedia.org/wiki/Search_engine_indexing#The_forward_index">forward index data structure</>:
 * an index indented to hold a mappings of inputId-s to contained keys.
 */
@ApiStatus.Experimental
public interface ForwardIndex<Key, Value> {

  interface Dumpable<K, V> extends ForwardIndex<K, V> {
    void dump();
  }
  /**
   * Creates a diff builder for given inputId.
   */
  @NotNull
  InputDataDiffBuilder<Key, Value> getDiffBuilder(int inputId) throws IOException;

  /**
   * Update data for inputId.
   */
  void putInputData(int inputId, @NotNull Map<Key, Value> data) throws IOException;

  void flush();

  void clear() throws IOException;

  void close() throws IOException;
}
