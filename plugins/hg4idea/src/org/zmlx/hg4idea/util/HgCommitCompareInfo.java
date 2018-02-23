// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.changes.Change;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.log.HgCommit;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.*;

public class HgCommitCompareInfo {

  private static final Logger LOG = Logger.getInstance(HgCommitCompareInfo.class);

  private final Map<HgRepository, Pair<List<HgCommit>, List<HgCommit>>> myInfo = new HashMap<>();
  private final Map<HgRepository, Collection<Change>> myTotalDiff = new HashMap<>();
  private final InfoType myInfoType;

  public HgCommitCompareInfo() {
    this(InfoType.BOTH);
  }

  public HgCommitCompareInfo(@NotNull InfoType infoType) {
    myInfoType = infoType;
  }

  public void put(@NotNull HgRepository repository, @NotNull Pair<List<HgCommit>, List<HgCommit>> commits) {
    myInfo.put(repository, commits);
  }

  public void put(@NotNull HgRepository repository, @NotNull Collection<Change> totalDiff) {
    myTotalDiff.put(repository, totalDiff);
  }

  @NotNull
  public List<HgCommit> getHeadToBranchCommits(@NotNull HgRepository repo) {
    return getCompareInfo(repo).getFirst();
  }

  @NotNull
  public List<HgCommit> getBranchToHeadCommits(@NotNull HgRepository repo) {
    return getCompareInfo(repo).getSecond();
  }

  @NotNull
  private Pair<List<HgCommit>, List<HgCommit>> getCompareInfo(@NotNull HgRepository repo) {
    Pair<List<HgCommit>, List<HgCommit>> pair = myInfo.get(repo);
    if (pair == null) {
      LOG.error("Compare info not found for repository " + repo);
      return Pair.create(Collections.<HgCommit>emptyList(), Collections.<HgCommit>emptyList());
    }
    return pair;
  }

  @NotNull
  public Collection<HgRepository> getRepositories() {
    return myInfo.keySet();
  }

  public boolean isEmpty() {
    return myInfo.isEmpty();
  }

  public InfoType getInfoType() {
    return myInfoType;
  }

  @NotNull
  public List<Change> getTotalDiff() {
    List<Change> changes = new ArrayList<>();
    for (Collection<Change> changeCollection : myTotalDiff.values()) {
      changes.addAll(changeCollection);
    }
    return changes;
  }

  public enum InfoType {
    BOTH, HEAD_TO_BRANCH, BRANCH_TO_HEAD
  }
}
