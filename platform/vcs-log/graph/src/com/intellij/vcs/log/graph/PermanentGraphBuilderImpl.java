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

import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class PermanentGraphBuilderImpl<CommitId> implements PermanentGraphBuilder<CommitId> {

  private final GraphColorManager<CommitId> STUB_COLOR_MANAGER = new GraphColorManager<CommitId>() {
    @Override
    public int getColorOfBranch(CommitId headCommit) {
      return headCommit.hashCode();
    }

    @Override
    public int getColorOfFragment(CommitId headCommit, int magicIndex) {
      return magicIndex;
    }

    @Override
    public int compareHeads(CommitId head1, CommitId head2) {
      return 0;
    }
  };

  @NotNull
  @Override
  public PermanentGraph<CommitId> build(@NotNull List<GraphCommit<CommitId>> commits) {
    return PermanentGraphImpl.newInstance(commits, STUB_COLOR_MANAGER, Collections.<CommitId>emptySet());
  }

}
