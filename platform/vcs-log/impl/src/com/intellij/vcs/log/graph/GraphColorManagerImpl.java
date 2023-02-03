// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.data.RefsModel;
import org.jetbrains.annotations.NotNull;

public final class GraphColorManagerImpl implements GraphColorManager<Integer> {
  static final int DEFAULT_COLOR = 0;
  private final @NotNull RefsModel myRefsModel;

  public GraphColorManagerImpl(@NotNull RefsModel refsModel) {
    myRefsModel = refsModel;
  }

  @Override
  public int getColorOfBranch(Integer headCommit) {
    VcsRef firstRef = myRefsModel.bestRefToHead(headCommit);
    if (firstRef == null) {
      return DEFAULT_COLOR;
    }
    // TODO dark variant
    return firstRef.getName().hashCode();
  }

  @Override
  public int getColorOfFragment(Integer headCommit, int magicIndex) {
    return magicIndex;
  }
}
