// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.main.collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.decompiler.code.cfg.ControlFlowGraph;
import org.jetbrains.java.decompiler.util.SFormsFastMapDirect;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences.*;

public class LimitContainer {

  private final int maxDirectNodeCount;//-1 - don't check
  @NotNull
  private final AtomicLong directNodeCount = new AtomicLong();
  @NotNull
  private final String maxDirectNodeCountMessage;

  private final int ssaConstructorSparseExRecordCount;//-1 - don't check
  @NotNull
  private final String ssaConstructorSparseExRecordCountMessage;

  public LimitContainer(@NotNull Map<String, Object> properties) {
    maxDirectNodeCount = (int)properties.getOrDefault(MAX_DIRECT_NODES_COUNT, -1);
    maxDirectNodeCountMessage = (String)properties.getOrDefault(MAX_DIRECT_NODES_COUNT_MESSAGE, "Limits are exceeded");
    ssaConstructorSparseExRecordCount = (int)properties.getOrDefault(MAX_DIRECT_VARIABLE_NODE_COUNT, -1);
    ssaConstructorSparseExRecordCountMessage = (String)properties.getOrDefault(MAX_DIRECT_VARIABLE_NODES_COUNT_MESSAGE, "Limits are exceeded");
  }

  public void incrementAndCheckDirectNodeCount(@NotNull ControlFlowGraph graph) {
    long newValue = directNodeCount.addAndGet(graph.getBlocks().size());
    if (maxDirectNodeCount != -1 && newValue >= maxDirectNodeCount) {
      throw new LimitExceededDecompilerException(maxDirectNodeCountMessage);
    }
  }

  public void checkSFormsFastMapDirect(@NotNull Map<String, SFormsFastMapDirect> inVarVersions,
                                       @NotNull Map<String, SFormsFastMapDirect> outVarVersions) {
    if (ssaConstructorSparseExRecordCount != -1 &&
        inVarVersions.size() + outVarVersions.size() > ssaConstructorSparseExRecordCount) {
      throw new LimitExceededDecompilerException(ssaConstructorSparseExRecordCountMessage);
    }
  }

  public static class LimitExceededDecompilerException extends RuntimeException {
    public LimitExceededDecompilerException(String message) {
      super(message);
    }
  }
}
