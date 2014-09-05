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

package com.intellij.vcs.log.graph.impl;

import com.intellij.util.Function;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.parser.CommitParser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class CommitIdManager<CommitId> {
  public static final CommitIdManager<String> STRING_COMMIT_ID_MANAGER = new CommitIdManager<String>() {
    @NotNull
    @Override
    public List<GraphCommit<String>> parseCommitList(@NotNull String in) {
      return CommitParser.parseStringCommitList(in);
    }

    @NotNull
    @Override
    public String toStr(String commit) {
      return commit;
    }

    @NotNull
    @Override
    public Integer toInt(@NotNull String commit) {
      return CommitParser.createHash(commit);
    }
  };

  public static final CommitIdManager<Integer> INTEGER_COMMIT_ID_MANAGER = new CommitIdManager<Integer>() {
    @NotNull
    @Override
    public List<GraphCommit<Integer>> parseCommitList(@NotNull String in) {
      return CommitParser.parseIntegerCommitList(in);
    }

    @NotNull
    @Override
    public String toStr(Integer commit) {
      return Integer.toHexString(commit);
    }

    @NotNull
    @Override
    public Integer toInt(@NotNull Integer commit) {
      return commit;
    }
  };

  @NotNull
  public abstract List<GraphCommit<CommitId>> parseCommitList(@NotNull String in);

  @NotNull
  public abstract String toStr(CommitId commit);

  @NotNull
  public abstract Integer toInt(@NotNull CommitId commit);

  @NotNull
  public Function<CommitId, String> getToStrFunction() {
    return new Function<CommitId, String>() {
      @Override
      public String fun(CommitId commitId) {
        return toStr(commitId);
      }
    };
  }
}
