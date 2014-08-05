package com.intellij.vcs.log.data;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class VcsLogMultiRepoJoiner<CommitId> {

  @NotNull
  public List<? extends GraphCommit<CommitId>> join(@NotNull Collection<List<? extends GraphCommit<CommitId>>> logsFromRepos) {
    if (logsFromRepos.size() == 1) {
      return logsFromRepos.iterator().next();
    }

    int size = 0;
    for (List<? extends GraphCommit<CommitId>> repo : logsFromRepos) {
      size += repo.size();
    }
    List<GraphCommit<CommitId>> result = new ArrayList<GraphCommit<CommitId>>(size);

    Map<GraphCommit<CommitId>, Iterator<? extends GraphCommit<CommitId>>> nextCommits = ContainerUtil.newHashMap();
    for (List<? extends GraphCommit<CommitId>> log : logsFromRepos) {
      Iterator<? extends GraphCommit<CommitId>> iterator = log.iterator();
      if (iterator.hasNext()) {
        nextCommits.put(iterator.next(), iterator);
      }
    }

    while (!nextCommits.isEmpty()) {
      GraphCommit<CommitId> lastCommit = findLatestCommit(nextCommits.keySet());
      Iterator<? extends GraphCommit<CommitId>> iterator = nextCommits.get(lastCommit);
      result.add(lastCommit);
      nextCommits.remove(lastCommit);

      if (iterator.hasNext()) {
        nextCommits.put(iterator.next(), iterator);
      }
    }

    return result;
  }

  @NotNull
  private GraphCommit<CommitId> findLatestCommit(@NotNull Set<GraphCommit<CommitId>> commits) {
    long maxTimeStamp = Long.MIN_VALUE;
    GraphCommit<CommitId> lastCommit = null;
    for (GraphCommit<CommitId> commit : commits) {
      if (commit.getTimestamp() >= maxTimeStamp) {
        maxTimeStamp = commit.getTimestamp();
        lastCommit = commit;
      }
    }
    assert lastCommit != null;
    return lastCommit;
  }

}
