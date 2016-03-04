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
import com.intellij.vcs.log.util.BekUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

class GitBekParentFixer {
  @NotNull private static final String MAGIC_TEXT = "Merge remote";
  @NotNull private static final VcsLogFilterCollection MAGIC_FILTER = createVcsLogFilterCollection();

  @NotNull private final Set<Hash> myWrongCommits;

  private GitBekParentFixer(@NotNull Set<Hash> wrongCommits) {
    myWrongCommits = wrongCommits;
  }

  @NotNull
  static GitBekParentFixer prepare(@NotNull VirtualFile root, @NotNull GitLogProvider provider) throws VcsException {
    if (!BekUtil.isBekEnabled()) {
      return new GitBekParentFixer(Collections.<Hash>emptySet());
    }
    return new GitBekParentFixer(getWrongCommits(provider, root));
  }

  @NotNull
  TimedVcsCommit fixCommit(@NotNull TimedVcsCommit commit) {
    if (!myWrongCommits.contains(commit.getId())) {
      return commit;
    }
    return reverseParents(commit);
  }

  @NotNull
  private static Set<Hash> getWrongCommits(@NotNull GitLogProvider provider, @NotNull VirtualFile root) throws VcsException {
    List<TimedVcsCommit> commitsMatchingFilter = provider.getCommitsMatchingFilter(root, MAGIC_FILTER, -1);
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
      public VcsLogHashFilter getHashFilter() {
        return null;
      }

      @Nullable
      @Override
      public VcsLogStructureFilter getStructureFilter() {
        return null;
      }

      @Nullable
      @Override
      public VcsLogRootFilter getRootFilter() {
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
