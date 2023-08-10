// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
  public abstract @NotNull IndexId<Key, Value> getName();

  /**
   * @return indexer which determines the procedure how input should be transformed to indexed data
   */
  public abstract @NotNull DataIndexer<Key, Value, Input> getIndexer();

  public abstract @NotNull KeyDescriptor<Key> getKeyDescriptor();

  public abstract @NotNull DataExternalizer<Value> getValueExternalizer();

  public abstract int getVersion();
}
