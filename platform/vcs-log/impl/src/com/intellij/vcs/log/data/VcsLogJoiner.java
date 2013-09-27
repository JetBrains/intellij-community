/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.TimedVcsCommit;
import com.intellij.vcs.log.VcsCommit;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Attaches the block of latest commits, which was read from the VCS, to the existing log structure.
 *
 * @author Stanislav Erokhin
 * @author Kirill Likhodedov
 */
public class VcsLogJoiner {

  /**
   * Attaches the block of latest commits, which was read from the VCS, to the existing log structure.
   *
   * @param savedLog       currently available part of the log.
   * @param previousRefs   references saved from the previous refresh.
   * @param firstBlock     the first n commits read from the VCS.
   * @param newRefs        all references (branches) of the repository.
   * @return Total saved log with new commits properly attached to it + number of new commits attached to the log.
   */
  @NotNull
  public Pair<List<TimedVcsCommit>, Integer> addCommits(@NotNull List<TimedVcsCommit> savedLog,
                                                           @NotNull Collection<VcsRef> previousRefs,
                                                           @NotNull List<? extends TimedVcsCommit> firstBlock,
                                                           @NotNull Collection<VcsRef> newRefs) {
    int unsafeBlockSize = getFirstSafeIndex(savedLog, firstBlock, newRefs);
    if (unsafeBlockSize == -1) { // firstBlock not enough
      //TODO
      throw new IllegalStateException();
    }

    List<TimedVcsCommit> unsafePartSavedLog = new ArrayList<TimedVcsCommit>(savedLog.subList(0, unsafeBlockSize));
    Set<TimedVcsCommit> allNewsCommits = getAllNewCommits(unsafePartSavedLog, firstBlock);
    unsafePartSavedLog = new NewCommitIntegrator(unsafePartSavedLog, allNewsCommits).getResultList();

    return Pair.create(ContainerUtil.concat(unsafePartSavedLog, savedLog.subList(unsafeBlockSize, savedLog.size())),
                       allNewsCommits.size());
  }

  /**
   *
   * @param savedLog       currently available part of the log.
   * @param firstBlock     the first n commits read from the VCS.
   * @param refs           all references (branches) of the repository.
   * @return first index i in savedLog, where all log after i is valid part of new log
   * -1 if not enough commits in firstBlock
   */
  private static int getFirstSafeIndex(@NotNull List<TimedVcsCommit> savedLog,
                                       @NotNull List<? extends TimedVcsCommit> firstBlock,
                                       @NotNull Collection<VcsRef> refs) {
    Set<Hash> allUnresolvedLinkedHashes = new HashSet<Hash>();
    for (VcsRef ref: refs) {
      allUnresolvedLinkedHashes.add(ref.getCommitHash());
    }
    for (VcsCommit commit : firstBlock) {
      allUnresolvedLinkedHashes.addAll(commit.getParents());
    }
    for (VcsCommit commit : firstBlock) {
      if (commit.getParents().size() != 0) {
        allUnresolvedLinkedHashes.remove(commit.getHash());
      }
    }
    return getFirstUnTrackedIndex(savedLog, allUnresolvedLinkedHashes);
  }

  private static int getFirstUnTrackedIndex(@NotNull List<TimedVcsCommit> commits, @NotNull Set<Hash> searchHashes) {
    int lastIndex = 0;
    for (VcsCommit commit : commits) {
      if (searchHashes.size() == 0) {
        return lastIndex;
      }
      searchHashes.remove(commit.getHash());
      lastIndex++;
    }
    if (searchHashes.size() == 0) {
      return lastIndex;
    } else {
      return -1;
    }
  }

  private static Set<TimedVcsCommit> getAllNewCommits(@NotNull List<TimedVcsCommit> unsafePartSavedLog,
                                                         @NotNull List<? extends TimedVcsCommit> firstBlock) {
    Set<Hash> existedCommitHashes = new HashSet<Hash>();
    for (VcsCommit commit : unsafePartSavedLog) {
      existedCommitHashes.add(commit.getHash());
    }
    Set<TimedVcsCommit> allNewsCommits = ContainerUtil.newHashSet();
    for (TimedVcsCommit newCommit : firstBlock) {
      if (!existedCommitHashes.contains(newCommit.getHash())) {
        allNewsCommits.add(newCommit);
      }
    }
    return allNewsCommits;
  }


  private static class NewCommitIntegrator {
    private final List<TimedVcsCommit> list;
    private final Map<Hash, TimedVcsCommit> newCommitsMap;

    private NewCommitIntegrator(@NotNull List<TimedVcsCommit> list, @NotNull Set<TimedVcsCommit> newCommits) {
      this.list = list;
      newCommitsMap = ContainerUtil.newHashMap();
      for (TimedVcsCommit commit : newCommits) {
        newCommitsMap.put(commit.getHash(), commit);
      }
    }

    // return insert Index
    private int insertToList(@NotNull TimedVcsCommit commit) {
      if (!newCommitsMap.containsKey(commit.getHash())) {
        throw new IllegalStateException("Commit was inserted, but insert call again. Commit hash: " + commit.getHash());
      }
      //insert all parents commits
      for (Hash parentHash : commit.getParents()) {
        TimedVcsCommit parentCommit = newCommitsMap.get(parentHash);
        if (parentCommit != null) {
          insertToList(parentCommit);
        }
      }

      int insertIndex = getInsertIndex(commit.getParents());
      list.add(insertIndex, commit);
      newCommitsMap.remove(commit.getHash());
      return insertIndex;
    }

    private int getInsertIndex(@NotNull Collection<Hash> parentHashes) {
      if (parentHashes.size() == 0) {
        return 0;
      }
      for (int i = 0; i < list.size(); i++) {
        if (parentHashes.contains(list.get(i).getHash())) {
          return i;
        }
      }
      throw new IllegalStateException("Not found parent Hash in list.");
    }

    private void insertAllCommits() {
      Iterator<TimedVcsCommit> iterator = newCommitsMap.values().iterator();
      while (iterator.hasNext()) {
        insertToList(iterator.next());
        iterator = newCommitsMap.values().iterator();
      }
    }

    private List<TimedVcsCommit> getResultList() {
      insertAllCommits();
      return list;
    }
  }


}
