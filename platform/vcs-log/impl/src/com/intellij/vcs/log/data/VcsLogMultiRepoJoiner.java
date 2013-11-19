package com.intellij.vcs.log.data;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.TimedVcsCommit;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Kirill Likhodedov
 */
class VcsLogMultiRepoJoiner {

  @NotNull
  public List<TimedVcsCommit> join(@NotNull Collection<List<? extends TimedVcsCommit>> logsFromRepos) {
    int size = 0;
    for (List<? extends TimedVcsCommit> repo : logsFromRepos) {
      size += repo.size();
    }
    List<TimedVcsCommit> result = new ArrayList<TimedVcsCommit>(size);

    Map<TimedVcsCommit, Iterator<? extends TimedVcsCommit>> nextCommits = ContainerUtil.newHashMap();
    for (List<? extends TimedVcsCommit> log : logsFromRepos) {
      Iterator<? extends TimedVcsCommit> iterator = log.iterator();
      if (iterator.hasNext()) {
        nextCommits.put(iterator.next(), iterator);
      }
    }

    while (!nextCommits.isEmpty()) {
      TimedVcsCommit lastCommit = findLatestCommit(nextCommits.keySet());
      Iterator<? extends TimedVcsCommit> iterator = nextCommits.get(lastCommit);
      result.add(lastCommit);
      nextCommits.remove(lastCommit);

      if (iterator.hasNext()) {
        nextCommits.put(iterator.next(), iterator);
      }
    }

    return result;
  }

  @NotNull
  private static TimedVcsCommit findLatestCommit(@NotNull Set<TimedVcsCommit> commits) {
    long maxTimeStamp = 0;
    TimedVcsCommit lastCommit = null;
    for (TimedVcsCommit commit : commits) {
      if (commit.getTime() > maxTimeStamp) {
        maxTimeStamp = commit.getTime();
        lastCommit = commit;
      }
    }
    assert lastCommit != null;
    return lastCommit;
  }

}
