// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.vcs.log.graph.impl.permanent;


import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public final class DuplicateParentFixer {

  public static <CommitId> AbstractList<? extends GraphCommit<CommitId>> fixDuplicateParentCommits(final List<? extends GraphCommit<CommitId>> finalCommits) {
    return new AbstractList<>() {
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

  private static final class DelegateGraphCommit<CommitId> implements GraphCommit<CommitId> {
    private final @NotNull GraphCommit<CommitId> myDelegate;

    private final @NotNull List<CommitId> myParents;

    private DelegateGraphCommit(@NotNull GraphCommit<CommitId> delegate, @NotNull List<CommitId> parents) {
      myDelegate = delegate;
      myParents = parents;
    }

    @Override
    public @NotNull CommitId getId() {
      return myDelegate.getId();
    }

    @Override
    public @NotNull List<CommitId> getParents() {
      return myParents;
    }

    @Override
    public long getTimestamp() {
      return myDelegate.getTimestamp();
    }
  }

  private static @NotNull <CommitId> GraphCommit<CommitId> fixParentsDuplicate(@NotNull GraphCommit<CommitId> commit) {
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

    List<CommitId> correctParents = new ArrayList<>();
    for (CommitId commitId : parents) {
      if (allParents.remove(commitId)) {
        correctParents.add(commitId);
      }
    }

    return new DelegateGraphCommit<>(commit, correctParents);
  }
}
