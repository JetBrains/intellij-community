// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main.collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.util.SFormsFastMapDirect;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences.MAX_DIRECT_NODES_COUNT;
import static org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences.MAX_DIRECT_VARIABLE_NODE_COUNT;

public class LimitContainer {

  private final int maxDirectNodeCount;//-1 - don't check
  @NotNull
  private final AtomicLong directNodeCount = new AtomicLong();

  private final int ssaConstructorSparseExRecordCount;//-1 - don't check

  public LimitContainer(@NotNull Map<String, Object> properties) {
    maxDirectNodeCount = (int)properties.getOrDefault(MAX_DIRECT_NODES_COUNT, -1);
    ssaConstructorSparseExRecordCount = (int)properties.getOrDefault(MAX_DIRECT_VARIABLE_NODE_COUNT, -1);
  }

  public void incrementAndCheckDirectNodeCount(@NotNull ControlFlowGraph graph) {
    long newValue = directNodeCount.addAndGet(graph.getBlocks().size());
    if (maxDirectNodeCount != -1 && newValue >= maxDirectNodeCount) {
      throw new LimitExceededDecompilerException(maxDirectNodeCount, newValue, "direct nodes");
    }
  }

  public void checkSFormsFastMapDirect(@NotNull Map<String, SFormsFastMapDirect> inVarVersions,
                                       @NotNull Map<String, SFormsFastMapDirect> outVarVersions) {
    int newValue = inVarVersions.size() + outVarVersions.size();
    if (ssaConstructorSparseExRecordCount != -1 &&
        newValue > ssaConstructorSparseExRecordCount) {
      throw new LimitExceededDecompilerException(ssaConstructorSparseExRecordCount, newValue, "variable nodes");
    }
  }

  public static class LimitExceededDecompilerException extends RuntimeException {
    public LimitExceededDecompilerException(long limit, long actualValue, String type) {
      super("Limits for %s are exceeded. Current value: %s, limit: %s".formatted(type, actualValue, limit));
    }
  }
}
