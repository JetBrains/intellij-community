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
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.locks.Lock;

public interface AbstractUpdatableIndex<Key, Value, Input> {
  void clear() throws StorageException;

  void flush() throws StorageException;

  /**
   * @param inputId *positive* id of content.
   */
  @NotNull
  Computable<Boolean> update(int inputId, @Nullable Input content);

  @NotNull
  ValueContainer<Value> getData(@NotNull Key key) throws StorageException;

  boolean processAllKeys(@NotNull Processor<Key> processor) throws StorageException;

  @NotNull
  Lock getReadLock();

  @NotNull
  Lock getWriteLock();

  void dispose();

  void requestRebuild(@Nullable Exception ex);

  @NotNull
  Class<? extends RuntimeException> getProcessCanceledExceptionClass();
}
