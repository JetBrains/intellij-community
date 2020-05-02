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

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Represents index data format specification, namely
 * serialization format for keys & values,
 * and a mapping from input to indexing data.
 *
 * To create index corresponding to any extension
 * one could use {@link com.intellij.util.indexing.impl.MapReduceIndex}.
 */
public abstract class IndexExtension<Key, Value, Input> {
  /**
   * @return unique name identifier of index extension
   */
  @NotNull
  public abstract IndexId<Key, Value> getName();

  /**
   * @return indexer which determines the procedure how input should be transformed to indexed data
   */
  @NotNull
  public abstract DataIndexer<Key, Value, Input> getIndexer();

  @NotNull
  public abstract KeyDescriptor<Key> getKeyDescriptor();

  @NotNull
  public abstract DataExternalizer<Value> getValueExternalizer();

  public abstract int getVersion();
}
