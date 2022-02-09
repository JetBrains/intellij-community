// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade;

import com.intellij.vcs.log.graph.api.LinearGraph;
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo;
import com.intellij.vcs.log.graph.utils.LinearGraphUtils;
import org.jetbrains.annotations.NotNull;

public class BaseController implements LinearGraphController {
  @NotNull private final PermanentGraphInfo<?> myPermanentGraphInfo;

  public BaseController(@NotNull PermanentGraphInfo<?> permanentGraphInfo) {
    myPermanentGraphInfo = permanentGraphInfo;
  }

  @NotNull
  @Override
  public LinearGraph getCompiledGraph() {
    return myPermanentGraphInfo.getLinearGraph();
  }

  @NotNull
  @Override
  public LinearGraphAnswer performLinearGraphAction(@NotNull LinearGraphAction action) {
    return LinearGraphUtils.DEFAULT_GRAPH_ANSWER;
  }
}
