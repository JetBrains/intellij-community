/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Implement this interface to map input (such as a file) to a {@code Map<Key, Value>},
 * which will be associated with this input by the {@link InvertedIndex index}.
 *
 * @see com.intellij.util.indexing.SingleEntryIndexer
 * @see com.intellij.util.indexing.CompositeDataIndexer
 * @see com.intellij.util.indexing.SingleEntryCompositeIndexer
 */
public interface DataIndexer<Key, Value, Data> {
  /**
   * Map input to its associated data.
   */
  @NotNull
  Map<Key,Value> map(@NotNull Data inputData);
}
