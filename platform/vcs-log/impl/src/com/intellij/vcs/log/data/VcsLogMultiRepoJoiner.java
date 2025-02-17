// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data;

import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public final class VcsLogMultiRepoJoiner<CommitId, Commit extends GraphCommit<CommitId>> {
  public @NotNull List<Commit> join(@NotNull Collection<? extends List<Commit>> logsFromRepos) {
    if (logsFromRepos.size() == 1) {
      return logsFromRepos.iterator().next();
    }

    int size = 0;
    for (List<Commit> repo : logsFromRepos) {
      size += repo.size();
    }
    List<Commit> result = new ArrayList<>(size);

    Map<Commit, Iterator<Commit>> nextCommits = new HashMap<>();
    for (List<Commit> log : logsFromRepos) {
      Iterator<Commit> iterator = log.iterator();
      if (iterator.hasNext()) {
        nextCommits.put(iterator.next(), iterator);
      }
    }

    while (!nextCommits.isEmpty()) {
      Commit lastCommit = findLatestCommit(nextCommits.keySet());
      Iterator<Commit> iterator = nextCommits.get(lastCommit);
      result.add(lastCommit);
      nextCommits.remove(lastCommit);

      if (iterator.hasNext()) {
        nextCommits.put(iterator.next(), iterator);
      }
    }

    return result;
  }

  private @NotNull Commit findLatestCommit(@NotNull Set<? extends Commit> commits) {
    long maxTimeStamp = Long.MIN_VALUE;
    Commit lastCommit = null;
    for (Commit commit : commits) {
      if (commit.getTimestamp() >= maxTimeStamp) {
        maxTimeStamp = commit.getTimestamp();
        lastCommit = commit;
      }
    }
    assert lastCommit != null;
    return lastCommit;
  }
}
