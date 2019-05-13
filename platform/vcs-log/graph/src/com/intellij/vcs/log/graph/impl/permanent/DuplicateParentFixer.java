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

package com.intellij.vcs.log.graph.impl.permanent;


import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DuplicateParentFixer {

  public static <CommitId> AbstractList<? extends GraphCommit<CommitId>> fixDuplicateParentCommits(final List<? extends GraphCommit<CommitId>> finalCommits) {
    return new AbstractList<GraphCommit<CommitId>>() {
      @Override
      public GraphCommit<CommitId> get(int index) {
        return fixParentsDuplicate(finalCommits.get(index));
      }

      @Override
      public int size() {
        return finalCommits.size();
      }
    };
  }

  private static class DelegateGraphCommit<CommitId> implements GraphCommit<CommitId> {
    @NotNull private final GraphCommit<CommitId> myDelegate;

    @NotNull private final List<CommitId> myParents;

    private DelegateGraphCommit(@NotNull GraphCommit<CommitId> delegate, @NotNull List<CommitId> parents) {
      myDelegate = delegate;
      myParents = parents;
    }

    @NotNull
    @Override
    public CommitId getId() {
      return myDelegate.getId();
    }

    @NotNull
    @Override
    public List<CommitId> getParents() {
      return myParents;
    }

    @Override
    public long getTimestamp() {
      return myDelegate.getTimestamp();
    }
  }

  @NotNull
  private static <CommitId> GraphCommit<CommitId> fixParentsDuplicate(@NotNull GraphCommit<CommitId> commit) {
    List<CommitId> parents = commit.getParents();
    if (parents.size() <= 1) return commit;

    if (parents.size() == 2) {
      CommitId commitId0 = parents.get(0);
      if (!commitId0.equals(parents.get(1))) {
        return commit;
      }
      else {
        return new DelegateGraphCommit<>(commit, Collections.singletonList(commitId0));
      }
    }

    Set<CommitId> allParents = new HashSet<>(parents);
    if (parents.size() == allParents.size()) return commit;

    List<CommitId> correctParents = ContainerUtil.newArrayList();
    for (CommitId commitId : parents) {
      if (allParents.remove(commitId)) {
        correctParents.add(commitId);
      }
    }

    return new DelegateGraphCommit<>(commit, correctParents);
  }
}
