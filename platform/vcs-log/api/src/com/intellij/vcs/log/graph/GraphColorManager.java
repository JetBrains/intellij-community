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

public interface GraphColorManager<CommitId> {

  /**
   * Returns the color which should be used to draw the given branch.
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
   * Compares two head commits in terms of "importance" of reference labels pointing to these commits.
   * It is used to order branches, branch labels, and for branch coloring. <br/>
   * E.g. if branch1 is more important than branch2, its color will be reused by the subgraph below the point when these branches
   * were diverged.
   * <p/>
   * Then head1 is more important than head2, if its most important reference is more important than head2's most important reference
   * (if they are the same, next are compared).
   * <p/>
   * References are compared by the following logic (see {@link com.intellij.vcs.log.VcsLogRefManager}: <ul>
   * <li>Negative value is returned if first reference is <b>more</b> important than the second (i.e. it will be at the left in the log).
   * <li>Positive value is returned if first reference is <b>less</b> important than the second (i.e. it will be at the right in the log).
   * <li>Zero is returned if referenced are considered equally important.
   * </ul>
   * <p>
   */
  int compareHeads(CommitId head1, CommitId head2); // todo drop this

}
