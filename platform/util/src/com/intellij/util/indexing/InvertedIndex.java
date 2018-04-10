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

package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public interface InvertedIndex<Key, Value, Input> {
  @NotNull
  ValueContainer<Value> getData(@NotNull Key key) throws StorageException;

  /**
   * @param inputId *positive* id of content.
   */
  @NotNull
  Computable<Boolean> update(int inputId, @Nullable Input content);

  void flush() throws StorageException;

  void clear() throws StorageException;

  void dispose();
}
