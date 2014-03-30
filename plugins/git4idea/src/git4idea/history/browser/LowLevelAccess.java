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
import git4idea.history.wholeTree.CommitHashPlusParents;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public interface LowLevelAccess {
  VirtualFile getRoot();

  CachedRefs getRefs() throws VcsException;
  void loadCommits(final Collection<String> startingPoints,
                   final Collection<String> endPoints,
                   final Collection<ChangesFilter.Filter> filters,
                   final AsynchConsumer<GitHeavyCommit> consumer,
                   int useMaxCnt,
                   Getter<Boolean> isCanceled, SymbolicRefsI refs, final boolean topoOrder) throws VcsException;

  Collection<String> getBranchesWithCommit(final SHAHash hash) throws VcsException;
  Collection<String> getTagsWithCommit(final SHAHash hash) throws VcsException;

  void loadHashesWithParents(final @NotNull Collection<String> startingPoints, @NotNull final Collection<ChangesFilter.Filter> filters,
                             final AsynchConsumer<CommitHashPlusParents> consumer, Getter<Boolean> isCanceled, int useMaxCnt,
                             final boolean topoOrder) throws VcsException;
  List<GitHeavyCommit> getCommitDetails(final Collection<String> commitIds, SymbolicRefsI refs) throws VcsException;
}
