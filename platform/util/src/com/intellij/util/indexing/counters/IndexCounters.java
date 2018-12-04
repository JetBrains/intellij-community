/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing.counters;

import static com.google.wireless.android.sdk.stats.IntellijIndexingStats.Index;
import static com.google.wireless.android.sdk.stats.IntellijIndexingStats.Operation;
import static com.google.wireless.android.sdk.stats.IntellijIndexingStats.OperationStats;

import com.android.tools.analytics.Counter;
import com.android.tools.analytics.Counters;
import com.google.wireless.android.sdk.stats.IntellijIndexingStats;
import com.google.wireless.android.sdk.stats.IntellijIndexingStats.IndexStats;
import org.jetbrains.annotations.NotNull;


public class IndexCounters {
  private static final String ID_INDEX_NAME = "IdIndex";
  private static final String STUBS_INDEX_NAME = "Stubs";
  private static final String TRIGRAM_INDEX_NAME = "Trigram.Index";

  public static final IndexCounters idIndexCounters = new IndexCounters(ID_INDEX_NAME, Index.ID_INDEX);
  public static final IndexCounters stubIndexCounters = new IndexCounters(STUBS_INDEX_NAME, Index.STUB_INDEX);
  public static final IndexCounters trigramIndexCounters = new IndexCounters(TRIGRAM_INDEX_NAME, Index.TRIGRAM_INDEX);
  public static final IndexCounters otherIndexesCounters = new IndexCounters("Other", Index.OTHER);

  @NotNull
  public static IndexCounters getIndexCounters(@NotNull String indexName) {
    // NOTE: The names of hte counters below match the names of indexes in the source code.
    switch (indexName) {
      case ID_INDEX_NAME:
        return idIndexCounters;
      case STUBS_INDEX_NAME:
        return stubIndexCounters;
      case TRIGRAM_INDEX_NAME:
        return trigramIndexCounters;
      default:
        return otherIndexesCounters;
    }
  }

  @NotNull
  public static IntellijIndexingStats.Builder addAllIndexCountersAndReset(@NotNull IntellijIndexingStats.Builder builder) {
    builder.addIndexStats(idIndexCounters.toProto());
    builder.addIndexStats(stubIndexCounters.toProto());
    builder.addIndexStats(trigramIndexCounters.toProto());
    builder.addIndexStats(otherIndexesCounters.toProto());
    reset();
    return builder;
  }

  private static void reset() {
    idIndexCounters.resetCounter();
    stubIndexCounters.resetCounter();
    trigramIndexCounters.resetCounter();
    otherIndexesCounters.resetCounter();
  }

  @NotNull private final String myIndexName;
  @NotNull private final Index myStatId;

  @NotNull private final Counter myMapInputCounter;
  @NotNull private final Counter myWriteLockCounter;
  @NotNull private final Counter myUpdateCounter;
  @NotNull private final Counter myGetDataCounter;

  private IndexCounters(@NotNull String indexName, @NotNull Index statId) {
    myIndexName = indexName;
    myStatId = statId;

    myMapInputCounter = Counters.get("indexing/mapInput/" + indexName);
    myWriteLockCounter = Counters.get("indexing/writeLock/" + indexName);
    myUpdateCounter = Counters.get("indexing/Update/" + indexName);
    myGetDataCounter = Counters.get("indexing/getData/" + indexName);
  }

  @NotNull
  public String getIndexName() {
    return myIndexName;
  }

  @NotNull
  public Counter getMapInputCounter() {
    return myMapInputCounter;
  }

  @NotNull
  public Counter getWriteLockCounter() {
    return myWriteLockCounter;
  }

  @NotNull
  public Counter getUpdateCounter() {
    return myUpdateCounter;
  }

  @NotNull
  public Counter getGetDataCounter() {
    return myGetDataCounter;
  }

  private void resetCounter() {
    myMapInputCounter.reset();
    myWriteLockCounter.reset();
    myUpdateCounter.reset();
    myGetDataCounter.reset();
  }

  @NotNull
  private OperationStats toProto(@NotNull Counter counter, @NotNull Operation operation) {
    return OperationStats.newBuilder()
      .setOperation(operation)
      .setTotalCount(counter.getTotalCount())
      .setTotalCpuNanos(counter.getTotalCount())
      .setMaxCpuNanos((int)counter.getMaxCpuNanos())
      .setTotalWallNanos(counter.getTotalWallNanos())
      .setMaxWallNanos((int)counter.getMaxWallNanos())
      .build();
  }

  @NotNull
  private IndexStats toProto() {
    return IndexStats.newBuilder()
      .setIndex(myStatId)
      .addOperationStats(toProto(myMapInputCounter, Operation.MAP_INPUT))
      .addOperationStats(toProto(myWriteLockCounter, Operation.WRITE_LOCK))
      .addOperationStats(toProto(myUpdateCounter, Operation.UPDATE_DATA))
      .addOperationStats(toProto(myGetDataCounter, Operation.GET_DATA))
      .build();
  }
}
