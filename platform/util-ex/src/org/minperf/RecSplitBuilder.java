// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.minperf;

import org.minperf.universal.UniversalHash;

import java.util.Collection;

/**
 * A builder to generate a MPHF description, or to get an evaluator of a description.
 */
public final class RecSplitBuilder<T> {
  private final UniversalHash<T> hash;
  private int averageBucketSize = 256;
  private int leafSize = 10;
  private int maxChunkSize = Integer.MAX_VALUE;

  private RecSplitBuilder(UniversalHash<T> hash) {
    this.hash = hash;
  }

  /**
   * Create a new instance of the builder, with the given universal hash implementation.
   *
   * @param <T>  the type
   * @param hash the universal hash function
   * @return the builder
   */
  public static <T> RecSplitBuilder<T> newInstance(UniversalHash<T> hash) {
    return new RecSplitBuilder<>(hash);
  }

  public RecSplitBuilder<T> averageBucketSize(int averageBucketSize) {
    if (averageBucketSize < 4 || averageBucketSize > 64 * 1024) {
      throw new IllegalArgumentException("averageBucketSize out of range: " + averageBucketSize);
    }

    this.averageBucketSize = averageBucketSize;
    return this;
  }

  public RecSplitBuilder<T> leafSize(int leafSize) {
    if (leafSize < 1 || leafSize > 25) {
      throw new IllegalArgumentException("leafSize out of range: " + leafSize);
    }
    this.leafSize = leafSize;
    return this;
  }

  public RecSplitBuilder<T> maxChunkSize(int maxChunkSize) {
    this.maxChunkSize = maxChunkSize;
    return this;
  }

  /**
   * Generate the hash function description for a collection.
   * The entries in the collection must be unique.
   *
   * @param collection the collection
   * @return the hash function description
   */
  public BitBuffer generate(Collection<T> collection) {
    Settings s = new Settings(leafSize, averageBucketSize);
    return new Generator<>(hash, s, maxChunkSize).generate(collection);
  }

  public RecSplitEvaluator<T> buildEvaluator(BitBuffer description) {
    return new RecSplitEvaluator<>(description, hash, new Settings(leafSize, averageBucketSize));
  }
}
