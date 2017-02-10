/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.changes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.versionBrowser.CommittedChangeList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.Convertor;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitBranch;
import git4idea.GitBranchesSearcher;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.commands.GitSimpleHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.history.browser.SHAHash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitOutgoingChangesProvider implements VcsOutgoingChangesProvider<CommittedChangeList> {
  private final static Logger LOG = Logger.getInstance("#git4idea.changes.GitOutgoingChangesProvider");
  private final Project myProject;

  public GitOutgoingChangesProvider(Project project) {
    myProject = project;
  }

  public Pair<VcsRevisionNumber, List<CommittedChangeList>> getOutgoingChanges(final VirtualFile vcsRoot, final boolean findRemote)
    throws VcsException {
    LOG.debug("getOutgoingChanges root: " + vcsRoot.getPath());
    final GitBranchesSearcher searcher = new GitBranchesSearcher(myProject, vcsRoot, findRemote);
    if (searcher.getLocal() == null || searcher.getRemote() == null) {
      return new Pair<>(null, Collections.<CommittedChangeList>emptyList());
    }
    final GitRevisionNumber base = getMergeBase(myProject, vcsRoot, searcher.getLocal(), searcher.getRemote());
    if (base == null) {
      return new Pair<>(null, Collections.<CommittedChangeList>emptyList());
    }
    final List<GitCommittedChangeList> lists = GitUtil.getLocalCommittedChanges(myProject, vcsRoot, new Consumer<GitSimpleHandler>() {
      public void consume(final GitSimpleHandler handler) {
        handler.addParameters(base.asString() + "..HEAD");
      }
    });
    return new Pair<>(base, ObjectsConvertor.convert(lists, new Convertor<GitCommittedChangeList, CommittedChangeList>() {
      @Override
      public CommittedChangeList convert(GitCommittedChangeList o) {
        return o;
      }
    }));
  }

  @Nullable
  public VcsRevisionNumber getMergeBaseNumber(final VirtualFile anyFileUnderRoot) throws VcsException {
    LOG.debug("getMergeBaseNumber parameter: " + anyFileUnderRoot.getPath());
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final VirtualFile root = vcsManager.getVcsRootFor(anyFileUnderRoot);
    if (root == null) {
      LOG.info("VCS root not found");
      return null;
    }

    final GitBranchesSearcher searcher = new GitBranchesSearcher(myProject, root, true);
    if (searcher.getLocal() == null || searcher.getRemote() == null) {
      LOG.info("local or remote not found");
      return null;
    }
    final GitRevisionNumber base = getMergeBase(myProject, root, searcher.getLocal(), searcher.getRemote());
    LOG.debug("found base: " + ((base == null) ? null : base.asString()));
    return base;
  }

  public Collection<Change> filterLocalChangesBasedOnLocalCommits(final Collection<Change> localChanges, final VirtualFile vcsRoot) throws VcsException {
    final GitBranchesSearcher searcher = new GitBranchesSearcher(myProject, vcsRoot, true);
    if (searcher.getLocal() == null || searcher.getRemote() == null) {
      return new ArrayList<>(localChanges); // no information, better strict approach (see getOutgoingChanges() code)
    }
    final GitRevisionNumber base;
    try {
      base = getMergeBase(myProject, vcsRoot, searcher.getLocal(), searcher.getRemote());
    } catch (VcsException e) {
      LOG.info(e);
      return new ArrayList<>(localChanges);
    }
    if (base == null) {
      return new ArrayList<>(localChanges); // no information, better strict approach (see getOutgoingChanges() code)
    }
    final List<Pair<SHAHash, Date>> hashes = GitHistoryUtils.onlyHashesHistory(myProject,
                                                                               VcsUtil.getFilePath(vcsRoot), vcsRoot, (base.asString() + "..HEAD"));

    if (hashes.isEmpty()) return Collections.emptyList(); // no local commits
    final String first = hashes.get(0).getFirst().getValue(); // optimization
    final Set<String> localHashes = new HashSet<>();
    for (Pair<SHAHash, Date> hash : hashes) {
      localHashes.add(hash.getFirst().getValue());
    }
    final Collection<Change> result = new ArrayList<>();
    for (Change change : localChanges) {
      if (change.getBeforeRevision() != null) {
        final String changeBeforeRevision = change.getBeforeRevision().getRevisionNumber().asString().trim();
        if (first.equals(changeBeforeRevision) || localHashes.contains(changeBeforeRevision)) {
          result.add(change);
        }
      }
    }
    return result;
  }

  @Nullable
  public Date getRevisionDate(VcsRevisionNumber revision, FilePath file) {
    if (VcsRevisionNumber.NULL.equals(revision)) return null;
    try {
      return new Date(GitHistoryUtils.getAuthorTime(myProject, file, revision.asString()));
    }
    catch (VcsException e) {
      return null;
    }
  }

  /**
   * Get a merge base between the current branch and specified branch.
   * @return the common commit or null if the there is no common commit
   */
  @Nullable
  private static GitRevisionNumber getMergeBase(@NotNull Project project, @NotNull VirtualFile root,
                                                @NotNull GitBranch currentBranch, @NotNull GitBranch branch) throws VcsException {
    return GitHistoryUtils.getMergeBase(project, root, currentBranch.getFullName(), branch.getFullName());
  }
}
