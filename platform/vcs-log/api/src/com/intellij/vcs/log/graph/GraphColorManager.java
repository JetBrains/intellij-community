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

import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

public interface GraphColorManager {

  /**
   * Returns the color which should be used to draw the given branch.
   * @param headCommit branch head commit index.
   */
  @NotNull
  JBColor getColorOfBranch(int headCommit);

  /**
   * Returns the color which should be used to draw a not-main fragment of a branch
   * (e.g. there was master, then I've checked out a feature, made some commits and merged back to master - these my commits form such
   * a fragment which is the same branch in terms of VCS, but is a separate branch in terms of graph,
   * and therefore we may want to color it separately.
   *
   * @param headCommit commit of the branch head which the fragment belongs to.
   * @param magicIndex some magic index identifying the fragment (we don't know which one - it is some Graph internal thing).
   * @return the color which should be used to draw this fragment.
   */
  @NotNull
  JBColor getColorOfFragment(int headCommit, int magicIndex);

  /**
   * Returns 1, 0 or -1 if branch identified by commit {@code head1} is "more powerful", "equally powerful" or "less powerful"
   * than the branch identified by commit {@code head2}.
   * <p/>
   * If branch1 is more powerful than branch2, it means that its color will be reused by the subgraph below the point when these branches
   * were diverged.
   */
  int compareHeads(int head1, int head2);

}
