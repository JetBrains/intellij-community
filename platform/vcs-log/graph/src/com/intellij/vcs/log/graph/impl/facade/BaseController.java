// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class BaseController implements LinearGraphController {
  private final @NotNull PermanentGraphInfo<?> myPermanentGraphInfo;

  public BaseController(@NotNull PermanentGraphInfo<?> permanentGraphInfo) {
    myPermanentGraphInfo = permanentGraphInfo;
  }

  @Override
  public @NotNull LinearGraph getCompiledGraph() {
    return myPermanentGraphInfo.getLinearGraph();
  }

  @Override
  public @NotNull LinearGraphAnswer performLinearGraphAction(@NotNull LinearGraphAction action) {
    return LinearGraphUtils.DEFAULT_GRAPH_ANSWER;
  }
}
