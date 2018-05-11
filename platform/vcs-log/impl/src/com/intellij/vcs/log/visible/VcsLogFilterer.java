// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.visible;

import com.intellij.openapi.util.Pair;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.data.DataPack;
import com.intellij.vcs.log.graph.PermanentGraph;
import org.jetbrains.annotations.NotNull;

public interface VcsLogFilterer {

  boolean canFilterEmptyPack(@NotNull VcsLogFilterCollection filters);

  @NotNull
  Pair<VisiblePack, CommitCountStage> filter(@NotNull DataPack pack,
                                             @NotNull PermanentGraph.SortType type,
                                             @NotNull VcsLogFilterCollection filters,
                                             @NotNull CommitCountStage count);
}
