/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.graph;

import com.intellij.vcs.log.VcsLogRefManager;

public interface GraphColorManager<CommitId> {

  /**
   * Returns the color which should be used to draw the given branch.
   *
   * @param headCommit branch head commit index.
   */
  int getColorOfBranch(CommitId headCommit);

  /**
   * Returns the color which should be used to draw a not-main fragment of a branch
   * (e.g. there was master, then I've checked out a feature, made some commits and merged back to master - these my commits form such
   * a fragment which is the same branch in terms of VCS, but is a separate branch in terms of graph,
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
   * @see VcsLogRefManager#getBranchLayoutComparator()
   */
  int compareHeads(CommitId head1, CommitId head2);
}
