// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph;

public interface GraphColorManager<CommitId> {

  /**
   * Returns the color which should be used to draw the given branch.
   *
   * @param headCommit branch head commit index.
   */
  int getColorOfBranch(CommitId headCommit);

  /**
   * Returns the color for drawing a not-main fragment of a branch
   * (e.g. a feature branch which was merged into the main branch).
   * Such fragments belong to the same branch in terms of VCS, but could be considered separate from the main branch,
   * and therefore we may want to color it separately.
   *
   * @param headCommit commit of the branch head which the fragment belongs to.
   * @param magicIndex some magic index identifying the fragment (we don't know which one - it is some Graph internal thing).
   * @return the colorId which should be used to draw this fragment.
   */
  int getColorOfFragment(CommitId headCommit, int magicIndex);

  /**
   * Compares two head commits, which represent graph branches, by expected positions of these branches in the graph,
   * and thus by their "importance".
   * <p/>
   * If branch1 is more important than branch2, branch1 will be laid out more to the left from the branch2, and
   * the color of branch1 will be reused by the subgraph below the point when these branches have diverged.
   * <p/>
   * <ul>
   * <li><b>Negative</b> value is returned if the branch represented by {@code head1} should be laid out at the left,
   * i.e. if {@code head1} is more important than {@code head2}.
   * <li><b>Positive</b> value is returned if the branch represented by {@code head1} should be laid out at the right from {@code head2}.
   * i.e. if {@code head1} is less important than {@code head2}.
   * <li>Zero is returned if the given commits are equal.
   * </ul>
   *
   * @see com.intellij.vcs.log.VcsLogRefManager#getBranchLayoutComparator()
   */
  int compareHeads(CommitId head1, CommitId head2);
}
