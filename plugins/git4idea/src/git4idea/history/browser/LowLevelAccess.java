/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Consumer;
import git4idea.history.wholeTree.CommitHashPlusParents;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface LowLevelAccess {
  GitCommit getCommitByHash(final SHAHash hash);

  List<Pair<SHAHash,Date>> loadCommitHashes(final @NotNull Collection<String> startingPoints,
                                                   @NotNull final Collection<String> endPoints,
                                                   @NotNull final Collection<ChangesFilter.Filter> filters,
                                                   int useMaxCnt) throws VcsException;

  void loadCommits(final Collection<String> startingPoints, final Date beforePoint, final Date afterPoint,
                             final Collection<ChangesFilter.Filter> filtersIn, final Consumer<GitCommit> consumer,
                             int maxCnt, List<String> branches) throws VcsException;

  void loadCommits(final Collection<String> startingPoints, final Collection<String> endPoints, final Collection<ChangesFilter.Filter> filters,
                   final Consumer<GitCommit> consumer, final Collection<String> branches, int useMaxCnt) throws VcsException;

  Collection<String> getBranchesWithCommit(final SHAHash hash) throws VcsException;
  Collection<String> getTagsWithCommit(final SHAHash hash) throws VcsException;

  void loadAllBranches(final List<String> sink) throws VcsException;
  void loadAllTags(final List<String> sink) throws VcsException;

  void cherryPick(SHAHash hash) throws VcsException;
  void loadHashesWithParents(final @NotNull Collection<String> startingPoints, @NotNull final Collection<ChangesFilter.Filter> filters,
                                    final Consumer<CommitHashPlusParents> consumer) throws VcsException;
}
