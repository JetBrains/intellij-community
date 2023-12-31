// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

import org.jetbrains.annotations.NotNull;

public interface GraphColorManager<CommitId> {

  /**
   * Returns the color for drawing a fragment of a branch, based on the branch head, index of the fragment of the head commit
   * and index of the target fragment.
   * This allows to specify the color for not-main fragments separately
   * (e.g. a feature branch which was merged into the main branch),
   * since such fragments could be considered separate from the main branch,
   * even though they belong to the same branch in terms of VCS.
   *
   * @param headCommit commit of the branch head which the fragment belongs to.
   * @param headFragmentIndex index identifying the head commit fragment.
   * @param fragmentIndex index identifying the target fragment.
   * @return the colorId which should be used to draw this fragment.
   */
  int getColor(@NotNull CommitId headCommit, int headFragmentIndex, int fragmentIndex);
}
