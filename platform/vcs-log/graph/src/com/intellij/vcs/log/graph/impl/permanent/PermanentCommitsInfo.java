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
import com.intellij.vcs.log.graph.utils.TimestampGetter;
import com.intellij.vcs.log.graph.utils.impl.IntTimestampGetter;
import com.intellij.vcs.log.graph.GraphCommit;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PermanentCommitsInfo<CommitId> {

  @NotNull
  public static <CommitId> PermanentCommitsInfo<CommitId> newInstance(@NotNull final List<? extends GraphCommit<CommitId>> graphCommits) {
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

    List<CommitId> commitIdIndex = ContainerUtil.map(graphCommits, new Function<GraphCommit<CommitId>, CommitId>() {
      @Override
      public CommitId fun(GraphCommit<CommitId> graphCommit) {
        return graphCommit.getId();
      }
    });
    return new PermanentCommitsInfo<CommitId>(timestampGetter, commitIdIndex);
  }

  @NotNull
  private final TimestampGetter myTimestampGetter;

  @NotNull
  private final List<CommitId> myCommitIdIndexes;   // todo optimize for Integer

  public PermanentCommitsInfo(@NotNull TimestampGetter timestampGetter, @NotNull List<CommitId> commitIdIndex) {
    myTimestampGetter = timestampGetter;
    myCommitIdIndexes = commitIdIndex;
  }

  @NotNull
  public CommitId getCommitId(int permanentNodeIndex) {
    return myCommitIdIndexes.get(permanentNodeIndex);
  }

  public long getTimestamp(int permanentNodeIndex) {
    return myTimestampGetter.getTimestamp(permanentNodeIndex);
  }

  // todo optimize with special map
  public int getPermanentNodeIndex(@NotNull CommitId commitId) {
    return myCommitIdIndexes.indexOf(commitId);
  }

  public int size() {
    return myCommitIdIndexes.size();
  }

}
