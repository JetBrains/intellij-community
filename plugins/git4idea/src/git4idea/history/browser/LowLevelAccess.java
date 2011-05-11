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

import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.AsynchConsumer;
import git4idea.GitBranch;
import git4idea.history.wholeTree.CommitHashPlusParents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface LowLevelAccess {
  VirtualFile getRoot();

  void loadCommits(final Collection<String> startingPoints, final Date beforePoint, final Date afterPoint,
                             final Collection<ChangesFilter.Filter> filtersIn, final AsynchConsumer<GitCommit> consumer,
                             int maxCnt, SymbolicRefs refs) throws VcsException;

  SymbolicRefs getRefs() throws VcsException;
  void loadCommits(final Collection<String> startingPoints,
                   final Collection<String> endPoints,
                   final Collection<ChangesFilter.Filter> filters,
                   final AsynchConsumer<GitCommit> consumer,
                   int useMaxCnt,
                   Getter<Boolean> isCanceled, SymbolicRefs refs) throws VcsException;

  Collection<String> getBranchesWithCommit(final SHAHash hash) throws VcsException;
  Collection<String> getTagsWithCommit(final SHAHash hash) throws VcsException;

  @Nullable
  GitBranch loadLocalBranches(Collection<String> sink) throws VcsException;

  @Nullable
  GitBranch loadRemoteBranches(Collection<String> sink) throws VcsException;

  void loadAllBranches(final List<String> sink) throws VcsException;

  void loadAllTags(final Collection<String> sink) throws VcsException;

  /**
   * Cherry-picks the specified commit.
   * Doesn't autocommit - instead puts the changes into a separate changelist.
   * In the case of merge conflict provides the Conflict Resolver dialog.
   * @return true if all conflicts were resolved or there were no merge conflicts; false if unresolved files remain.
   * @throws VcsException
   */
  boolean cherryPick(GitCommit hash) throws VcsException;
  void loadHashesWithParents(final @NotNull Collection<String> startingPoints, @NotNull final Collection<ChangesFilter.Filter> filters,
                             final AsynchConsumer<CommitHashPlusParents> consumer, Getter<Boolean> isCanceled, int useMaxCnt) throws VcsException;
  List<GitCommit> getCommitDetails(final Collection<String> commitIds, SymbolicRefs refs) throws VcsException;
}
