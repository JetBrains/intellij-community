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

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.graph.api.permanent.PermanentCommitsInfo;
import com.intellij.vcs.log.graph.utils.IntList;
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import com.intellij.vcs.log.graph.utils.impl.CompressedIntList;
import com.intellij.vcs.log.graph.utils.impl.IntTimestampGetter;
import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PermanentCommitsInfoIml<CommitId> implements PermanentCommitsInfo<CommitId> {

  @NotNull
  public static <CommitId> PermanentCommitsInfoIml<CommitId> newInstance(@NotNull final List<? extends GraphCommit<CommitId>> graphCommits) {
    TimestampGetter timestampGetter = IntTimestampGetter.newInstance(new TimestampGetter() {
      @Override
      public int size() {
        return graphCommits.size();
      }

      @Override
      public long getTimestamp(int index) {
        return graphCommits.get(index).getTimestamp();
      }
    });

    boolean isIntegerCase = !graphCommits.isEmpty() && graphCommits.get(0).getId().getClass() == Integer.class;

    List<CommitId> commitIdIndex;
    if (isIntegerCase) {
      commitIdIndex = (List<CommitId>)createCompressedIntList((List<? extends GraphCommit<Integer>>)graphCommits);
    } else {
      commitIdIndex = ContainerUtil.map(graphCommits, new Function<GraphCommit<CommitId>, CommitId>() {
        @Override
        public CommitId fun(GraphCommit<CommitId> graphCommit) {
          return graphCommit.getId();
        }
      });
    }
    return new PermanentCommitsInfoIml<CommitId>(timestampGetter, commitIdIndex);
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

  @NotNull
  private final TimestampGetter myTimestampGetter;

  @NotNull
  private final List<CommitId> myCommitIdIndexes;

  public PermanentCommitsInfoIml(@NotNull TimestampGetter timestampGetter, @NotNull List<CommitId> commitIdIndex) {
    myTimestampGetter = timestampGetter;
    myCommitIdIndexes = commitIdIndex;
  }

  @Override
  @NotNull
  public CommitId getCommitId(int permanentNodeIndex) {
    return myCommitIdIndexes.get(permanentNodeIndex);
  }

  @Override
  public long getTimestamp(int permanentNodeIndex) {
    return myTimestampGetter.getTimestamp(permanentNodeIndex);
  }

  @NotNull
  public TimestampGetter getTimestampGetter() {
    return myTimestampGetter;
  }

  // todo optimize with special map
  @Override
  public int getPermanentNodeIndex(@NotNull CommitId commitId) {
    return myCommitIdIndexes.indexOf(commitId);
  }

  @NotNull
  public List<CommitId> convertToCommitIdList(@NotNull Collection<Integer> commitIndexes) {
    return ContainerUtil.map(commitIndexes, new Function<Integer, CommitId>() {
      @Override
      public CommitId fun(Integer integer) {
        return getCommitId(integer);
      }
    });
  }

  @NotNull
  public Set<CommitId> convertToCommitIdSet(@NotNull Collection<Integer> commitIndexes) {
    return ContainerUtil.map2Set(commitIndexes, new Function<Integer, CommitId>() {
      @Override
      public CommitId fun(Integer integer) {
        return getCommitId(integer);
      }
    });
  }

  @NotNull
  public Set<Integer> convertToCommitIndexes(@NotNull Collection<CommitId> commitIds) {
    Set<Integer> result = ContainerUtil.newHashSet();
    for (int i = 0; i < myCommitIdIndexes.size(); i++) {
      CommitId commitId = myCommitIdIndexes.get(i);
      if (commitIds.contains(commitId)) {
        result.add(i);
      }
    }
    return result;
  }

}
