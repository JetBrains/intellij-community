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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.VcsLogFilterCollectionImpl.VcsLogFilterCollectionBuilder;
import com.intellij.vcs.log.util.BekUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

class GitBekParentFixer {
  @NotNull private static final String MAGIC_TEXT = "Merge remote";
  @NotNull private static final VcsLogFilterCollection MAGIC_FILTER = createVcsLogFilterCollection();

  @NotNull private final Set<Hash> myIncorrectCommits;

  private GitBekParentFixer(@NotNull Set<Hash> incorrectCommits) {
    myIncorrectCommits = incorrectCommits;
  }

  @NotNull
  static GitBekParentFixer prepare(@NotNull VirtualFile root, @NotNull GitLogProvider provider) throws VcsException {
    if (!BekUtil.isBekEnabled()) {
      return new GitBekParentFixer(Collections.emptySet());
    }
    return new GitBekParentFixer(getIncorrectCommits(provider, root));
  }

  @NotNull
  TimedVcsCommit fixCommit(@NotNull TimedVcsCommit commit) {
    if (!myIncorrectCommits.contains(commit.getId())) {
      return commit;
    }
    return reverseParents(commit);
  }

  @NotNull
  private static Set<Hash> getIncorrectCommits(@NotNull GitLogProvider provider, @NotNull VirtualFile root) throws VcsException {
    List<TimedVcsCommit> commitsMatchingFilter = provider.getCommitsMatchingFilter(root, MAGIC_FILTER, -1);
    return ContainerUtil.map2Set(commitsMatchingFilter, timedVcsCommit -> timedVcsCommit.getId());
  }

  @NotNull
  private static TimedVcsCommit reverseParents(@NotNull TimedVcsCommit commit) {
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
    VcsLogTextFilter textFilter = new VcsLogTextFilter() {
      @Override
      public boolean matchesCase() {
        return false;
      }

      @Override
      public boolean isRegex() {
        return false;
      }

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

    return new VcsLogFilterCollectionBuilder().with(textFilter).build();
  }
}
