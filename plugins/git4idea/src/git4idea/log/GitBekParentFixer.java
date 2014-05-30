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
package git4idea.log;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.graph.impl.facade.bek.BekSorter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

class GitBekParentFixer {
  @NotNull
  private final static String MAGIC_TEXT = "Merge remote";
  @NotNull
  private final VcsLogFilterCollection MAGIC_FILTER = createVcsLogFilterCollection();

  @NotNull
  private final VirtualFile myRoot;
  @NotNull
  private final GitLogProvider myGitLogProvider;
  @NotNull
  private final List<TimedVcsCommit> myAllCommits;

  GitBekParentFixer(@NotNull VirtualFile root, @NotNull GitLogProvider gitLogProvider, @NotNull List<TimedVcsCommit> allCommits) {
    myRoot = root;
    myGitLogProvider = gitLogProvider;
    myAllCommits = allCommits;
  }

  @NotNull
  List<TimedVcsCommit> getCorrectCommits() throws VcsException {
    if (!BekSorter.isBekEnabled())
      return myAllCommits;

    final Set<Hash> wrongCommits = getWrongCommits();
    return new AbstractList<TimedVcsCommit>() {
      @Override
      public TimedVcsCommit get(int index) {
        TimedVcsCommit commit = myAllCommits.get(index);
        if (!wrongCommits.contains(commit.getId()))
          return commit;

        return reverseParents(commit);
      }

      @Override
      public int size() {
        return myAllCommits.size();
      }
    };
  }

  private Set<Hash> getWrongCommits() throws VcsException {
    List<TimedVcsCommit> commitsMatchingFilter = myGitLogProvider.getCommitsMatchingFilter(myRoot, MAGIC_FILTER, -1);
    return ContainerUtil.map2Set(commitsMatchingFilter, new Function<TimedVcsCommit, Hash>() {
      @Override
      public Hash fun(TimedVcsCommit timedVcsCommit) {
        return timedVcsCommit.getId();
      }
    });
  }

  @NotNull
  private static TimedVcsCommit reverseParents(@NotNull final TimedVcsCommit commit) {
    return new TimedVcsCommit() {
      @Override
      public long getTimestamp() {
        return commit.getTimestamp();
      }

      @NotNull
      @Override
      public Hash getId() {
        return commit.getId();
      }

      @NotNull
      @Override
      public List<Hash> getParents() {
        return ContainerUtil.reverse(commit.getParents());
      }
    };
  }

  private static VcsLogFilterCollection createVcsLogFilterCollection() {
    final VcsLogTextFilter textFilter = new VcsLogTextFilter() {
      @NotNull
      @Override
      public String getText() {
        return MAGIC_TEXT;
      }

      @Override
      public boolean matches(@NotNull VcsCommitMetadata details) {
        return details.getFullMessage().contains(MAGIC_TEXT);
      }
    };

    return new VcsLogFilterCollection() {
      @Nullable
      @Override
      public VcsLogBranchFilter getBranchFilter() {
        return null;
      }

      @Nullable
      @Override
      public VcsLogUserFilter getUserFilter() {
        return null;
      }

      @Nullable
      @Override
      public VcsLogDateFilter getDateFilter() {
        return null;
      }

      @Nullable
      @Override
      public VcsLogTextFilter getTextFilter() {
        return textFilter;
      }

      @Nullable
      @Override
      public VcsLogStructureFilter getStructureFilter() {
        return null;
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @NotNull
      @Override
      public List<VcsLogDetailsFilter> getDetailsFilters() {
        return Collections.<VcsLogDetailsFilter>singletonList(textFilter);
      }
    };
  }
}
