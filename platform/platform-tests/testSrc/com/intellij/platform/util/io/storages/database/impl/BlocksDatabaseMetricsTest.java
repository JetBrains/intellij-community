// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.database.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BlocksDatabaseMetricsTest {
  private static final int CHUNK_SIZE = 1024 * 1024;

  @Test
  public void metricsCombineComponentStateAndLifecycleEvents(@TempDir Path tempDirectory) throws Exception {
    try (var database = BlocksDatabaseImpl.open(tempDirectory, CHUNK_SIZE, true, false)) {
      var store = database.openStore("store", 1);
      var block = store.allocateBlock(17, 64);
      block.activate();
      block.seal();
      block.retire();
      store.allocateBlock(18, 64).discard();
      store.drop();

      var metrics = database.metrics(true);
      assertEquals(0, metrics.catalog().storesTotal());
      assertEquals(1, metrics.catalog().storesCreated());
      assertEquals(1, metrics.catalog().storesDropped());
      assertEquals(1, metrics.chunks().created());
      assertEquals(2, metrics.blocks().allocated());
      assertEquals(1, metrics.blocks().activated());
      assertEquals(1, metrics.blocks().discarded());
      assertEquals(1, metrics.blocks().sealed());
      assertEquals(1, metrics.blocks().retired());
      assertEquals(2, metrics.blocks().total());
      assertEquals(0, metrics.blocks().allocatedCurrent());
      assertEquals(0, metrics.blocks().activeCurrent());
      assertEquals(0, metrics.blocks().sealedCurrent());
      assertEquals(2, metrics.blocks().retiredCurrent());
      assertEquals(metrics, database.metrics(false), "The current implementation must also snapshot weak metrics requests");
    }
  }
}
