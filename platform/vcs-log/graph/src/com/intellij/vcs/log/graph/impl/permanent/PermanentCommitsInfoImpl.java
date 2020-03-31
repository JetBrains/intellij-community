// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.vcs.log.graph.impl.permanent;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.utils.IntList;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList;
import com.intellij.vcs.log.graph.utils.impl.IntTimestampGetter;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TIntObjectIterator;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PermanentCommitsInfoImpl<CommitId> implements PermanentCommitsInfo<CommitId> {
  private static final Logger LOG = Logger.getInstance(PermanentCommitsInfoImpl.class);

  @NotNull
  public static <CommitId> PermanentCommitsInfoImpl<CommitId> newInstance(@NotNull final List<? extends GraphCommit<CommitId>> graphCommits,
                                                                          @NotNull TIntObjectHashMap<CommitId> notLoadedCommits) {
    TimestampGetter timestampGetter = createTimestampGetter(graphCommits);

    boolean isIntegerCase = !graphCommits.isEmpty() && graphCommits.get(0).getId().getClass() == Integer.class;

    List<CommitId> commitIdIndex;
    if (isIntegerCase) {
      commitIdIndex = (List<CommitId>)createCompressedIntList((List<? extends GraphCommit<Integer>>)graphCommits);
    }
    else {
      commitIdIndex = ContainerUtil.map(graphCommits, (Function<GraphCommit<CommitId>, CommitId>)GraphCommit::getId);
    }
    return new PermanentCommitsInfoImpl<>(timestampGetter, commitIdIndex, notLoadedCommits);
  }

  @NotNull
  public static <CommitId> IntTimestampGetter createTimestampGetter(@NotNull final List<? extends GraphCommit<CommitId>> graphCommits) {
    return IntTimestampGetter.newInstance(new TimestampGetter() {
      @Override
      public int size() {
        return graphCommits.size();
      }

      @Override
      public long getTimestamp(int index) {
        return graphCommits.get(index).getTimestamp();
      }
    });
  }

  @NotNull
  private static List<Integer> createCompressedIntList(@NotNull final List<? extends GraphCommit<Integer>> graphCommits) {
    final IntList compressedIntList = CompressedIntList.newInstance(new IntList() {
      @Override
      public int size() {
        return graphCommits.size();
      }

      @Override
      public int get(int index) {
        return graphCommits.get(index).getId();
      }
    }, 30);
    return new AbstractList<Integer>() {
      @NotNull
      @Override
      public Integer get(int index) {
        return compressedIntList.get(index);
      }

      @Override
      public int size() {
        return compressedIntList.size();
      }
    };
  }

  @NotNull private final TimestampGetter myTimestampGetter;

  @NotNull private final List<? extends CommitId> myCommitIdIndexes;

  @NotNull private final TIntObjectHashMap<CommitId> myNotLoadCommits;

  private PermanentCommitsInfoImpl(@NotNull TimestampGetter timestampGetter,
                                   @NotNull List<? extends CommitId> commitIdIndex,
                                   @NotNull TIntObjectHashMap<CommitId> notLoadCommits) {
    myTimestampGetter = timestampGetter;
    myCommitIdIndexes = commitIdIndex;
    myNotLoadCommits = notLoadCommits;
  }

  @Override
  @NotNull
  public CommitId getCommitId(int nodeId) {
    if (nodeId < 0) return myNotLoadCommits.get(nodeId);
    return myCommitIdIndexes.get(nodeId);
  }

  @Override
  public long getTimestamp(int nodeId) {
    if (nodeId < 0) return 0;
    return myTimestampGetter.getTimestamp(nodeId);
  }

  @NotNull
  public TimestampGetter getTimestampGetter() {
    return myTimestampGetter;
  }

  // todo optimize with special map
  @Override
  public int getNodeId(@NotNull CommitId commitId) {
    int indexOf = myCommitIdIndexes.indexOf(commitId);
    if (indexOf != -1) return indexOf;

    return getNotLoadNodeId(commitId);
  }

  private int getNotLoadNodeId(@NotNull CommitId commitId) {
    TIntObjectIterator<CommitId> iterator = myNotLoadCommits.iterator();
    while (iterator.hasNext()) {
      iterator.advance();
      if (iterator.value().equals(commitId)) return iterator.key();
    }
    return -1;
  }

  @NotNull
  public List<CommitId> convertToCommitIdList(@NotNull Collection<Integer> commitIndexes) {
    return ContainerUtil.map(commitIndexes, this::getCommitId);
  }

  @NotNull
  public Set<CommitId> convertToCommitIdSet(@NotNull Collection<Integer> commitIndexes) {
    return ContainerUtil.map2Set(commitIndexes, this::getCommitId);
  }

  @Override
  @NotNull
  public Set<Integer> convertToNodeIds(@NotNull Collection<? extends CommitId> commitIds) {
    return convertToNodeIds(commitIds, false);
  }

  @NotNull
  public Set<Integer> convertToNodeIds(@NotNull Collection<? extends CommitId> commitIds, boolean reportNotFound) {
    Set<Integer> result = new HashSet<>();
    Set<CommitId> matchedIds = new HashSet<>();
    for (int i = 0; i < myCommitIdIndexes.size(); i++) {
      CommitId commitId = myCommitIdIndexes.get(i);
      if (commitIds.contains(commitId)) {
        result.add(i);
        matchedIds.add(commitId);
      }
    }
    if (reportNotFound) {
      Collection<CommitId> unmatchedIds = ContainerUtil.subtract(commitIds, matchedIds);
      if (!unmatchedIds.isEmpty()) {
        LOG.warn("Unmatched commit ids " + unmatchedIds);
      }
    }

    TIntObjectIterator<CommitId> iterator = myNotLoadCommits.iterator();
    while (iterator.hasNext()) {
      iterator.advance();
      CommitId value = iterator.value();
      if (commitIds.contains(value)) result.add(iterator.key());
    }
    return result;
  }

  public boolean containsAll(@NotNull Collection<? extends CommitId> commitIds) {
    Set<? extends CommitId> commitsToFind = new HashSet<>(commitIds);
    for (CommitId commitId : myCommitIdIndexes) {
      commitsToFind.remove(commitId);
      if (commitsToFind.isEmpty()) {
        return true;
      }
    }
    return false;
  }
}
