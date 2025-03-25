// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing;

import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Represents index data format specification, namely serialization format for keys & values, and a mapping from input to
 * indexing data.
 * <p>
 * To create index corresponding to any extension one could use {@link com.intellij.util.indexing.impl.MapReduceIndex}.
 */
public abstract class IndexExtension<Key, Value, Input> {
  /**
   * @return unique name identifier of index extension
   */
  public abstract @NotNull IndexId<Key, Value> getName();

  /** @return indexer which determines the procedure how input should be transformed to indexed data */
  public abstract @NotNull DataIndexer<Key, Value, Input> getIndexer();

  public abstract @NotNull KeyDescriptor<Key> getKeyDescriptor();

  public abstract @NotNull DataExternalizer<Value> getValueExternalizer();

  /**
   * Version of index format/algo.
   * Generally, if the version is changed -- index must be rebuilt.
   * <p>
   * Discussion: "index version" is quite ill-defined concept, because really it should be not a single version, but a few separate
   * versions for "binary layout to be written version", "binary layout compatible to read version", "indexer algo version", etc.
   * Currently, all those 'versions' are merged into a single version -- which simplifies things a bit, but causes issues from time to time.
   *
   * @see ShardableIndexExtension#shardlessVersion
   */
  public abstract int getVersion();
}
