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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.AsynchConsumer;
import git4idea.GitBranch;
import git4idea.GitTag;
import git4idea.GitUtil;
import git4idea.PlatformFacade;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBranchesCollection;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitConfigUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.history.wholeTree.AbstractHash;
import git4idea.history.wholeTree.CommitHashPlusParents;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryFiles;
import git4idea.repo.GitRepositoryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class LowLevelAccessImpl implements LowLevelAccess {
  private final static Logger LOG = Logger.getInstance("#git4idea.history.browser.LowLevelAccessImpl");
   // The name that specifies that git is on specific commit rather then on some branch ({@value})
  private static final String NO_BRANCH_NAME = "(no branch)";

  private final Project myProject;
  private final VirtualFile myRoot;

  public LowLevelAccessImpl(final Project project, final VirtualFile root) {
    myProject = project;
    myRoot = root;
  }

  @Override
  public VirtualFile getRoot() {
    return myRoot;
  }

  public void loadHashesWithParents(final @NotNull Collection<String> startingPoints,
                                    @NotNull final Collection<ChangesFilter.Filter> filters,
                                    final AsynchConsumer<CommitHashPlusParents> consumer,
                                    Getter<Boolean> isCanceled, int useMaxCnt, final boolean topoOrder) throws VcsException {
    final List<String> parameters = new ArrayList<String>();
    final Collection<VirtualFile> paths = new HashSet<VirtualFile>();
    ChangesFilter.filtersToParameters(filters, parameters, paths);

    if (! startingPoints.isEmpty()) {
      for (String startingPoint : startingPoints) {
        parameters.add(startingPoint);
      }
    } else {
      parameters.add("--all");
    }
    if (useMaxCnt > 0) {
      parameters.add("--max-count=" + useMaxCnt);
    }
    if (topoOrder) {
      parameters.add("--topo-order");
    } else {
      parameters.add("--date-order");
    }

    GitHistoryUtils.hashesWithParents(myProject, new FilePathImpl(myRoot), consumer, isCanceled, paths, ArrayUtil.toStringArray(parameters));
  }

  @Override
  public List<GitCommit> getCommitDetails(final Collection<String> commitIds, SymbolicRefsI refs) throws VcsException {
    return GitHistoryUtils.commitsDetails(myProject, new FilePathImpl(myRoot), refs, commitIds);
  }

  public void loadCommits(final Collection<String> startingPoints, final Date beforePoint, final Date afterPoint,
                             final Collection<ChangesFilter.Filter> filtersIn, final AsynchConsumer<GitCommit> consumer,
                             int maxCnt, SymbolicRefsI refs, final boolean topoOrder) throws VcsException {
    final Collection<ChangesFilter.Filter> filters = new ArrayList<ChangesFilter.Filter>(filtersIn);
    if (beforePoint != null) {
      filters.add(new ChangesFilter.BeforeDate(new Date(beforePoint.getTime() - 1)));
    }
    if (afterPoint != null) {
      filters.add(new ChangesFilter.AfterDate(afterPoint));
    }

    loadCommits(startingPoints, Collections.<String>emptyList(), filters, consumer, maxCnt, null, refs, topoOrder);
  }

  // uses cached version
  public CachedRefs getRefs() throws VcsException {
    final CachedRefs refs = new CachedRefs();
    GitRepository repository = myProject == null || myProject.isDefault() ? null : GitUtil.getRepositoryManager(myProject).getRepositoryForRoot(myRoot);
    if (repository == null) {
      final File child = new File(myRoot.getPath(), GitUtil.DOT_GIT);
      if (! child.exists()) {
        throw new VcsException("No git repository in " + myRoot.getPath());
      }
      repository = GitRepositoryImpl.getLightInstance(myRoot, myProject, ServiceManager.getService(myProject, PlatformFacade.class), myProject);
      repository.update(GitRepository.TrackedTopic.BRANCHES);
      repository.getBranches();
    }
    GitBranchesCollection branches = repository.getBranches();
    refs.setCollection(branches);
    final GitBranch current = repository.getCurrentBranch();
    refs.setCurrentBranch(current);
    if (current != null) {
      GitBranch tracked = GitBranchUtil.tracked(myProject, myRoot, current.getName());
      String fullName = tracked == null ? null : tracked.getFullName();
      fullName = fullName != null && fullName.startsWith(GitBranch.REFS_REMOTES_PREFIX) ? fullName.substring(GitBranch.REFS_REMOTES_PREFIX.length()) : fullName;
      refs.setTrackedRemoteName(fullName);
    }
    refs.setUsername(GitConfigUtil.getValue(myProject, myRoot, GitConfigUtil.USER_NAME));
    final VcsRevisionNumber head = GitHistoryUtils.getCurrentRevision(myProject, new FilePathImpl(myRoot), "HEAD", true);
    refs.setHeadHash(AbstractHash.create(head.asString()));
    return refs;
  }

  public void loadCommits(final @NotNull Collection<String> startingPoints, @NotNull final Collection<String> endPoints,
                          @NotNull final Collection<ChangesFilter.Filter> filters,
                          @NotNull final AsynchConsumer<GitCommit> consumer,
                          int useMaxCnt,
                          Getter<Boolean> isCanceled, SymbolicRefsI refs, final boolean topoOrder)
    throws VcsException {

    final List<String> parameters = new ArrayList<String>();
    if (useMaxCnt > 0) {
      parameters.add("--max-count=" + useMaxCnt);
    }

    final Collection<VirtualFile> paths = new HashSet<VirtualFile>();
    ChangesFilter.filtersToParameters(filters, parameters, paths);

    if (! startingPoints.isEmpty()) {
      for (String startingPoint : startingPoints) {
        parameters.add(startingPoint);
      }
    } else {
      parameters.add("--all");
    }
    if (topoOrder) {
      parameters.add("--topo-order");
    } else {
      parameters.add("--date-order");
    }

    for (String endPoint : endPoints) {
      parameters.add("^" + endPoint);
    }

    GitHistoryUtils.historyWithLinks(myProject, new FilePathImpl(myRoot),
                                     refs, consumer, isCanceled, paths, ArrayUtil.toStringArray(parameters));
  }

  public List<String> getBranchesWithCommit(final SHAHash hash) throws VcsException {
    return getBranchesWithCommit(hash.getValue());
  }

  public List<String> getBranchesWithCommit(final String hash) throws VcsException {
    return new ArrayList<String>(listAsStrings(myProject, myRoot, true, true, hash));
  }

  public Collection<String> getTagsWithCommit(final SHAHash hash) throws VcsException {
    final List<String> result = new ArrayList<String>();
    GitTag.listAsStrings(myProject, myRoot, result, hash.getValue());
    return result;
  }

  public void loadAllBranches(List<String> sink) throws VcsException {
    sink.addAll(listAsStrings(myProject, myRoot, true, false, null));
    sink.addAll(listAsStrings(myProject, myRoot, false, true, null));
  }

  public void loadAllTags(Collection<String> sink) throws VcsException {
    GitTag.listAsStrings(myProject, myRoot, sink, null);
  }

  @NotNull
  private static Collection<String> listAsStrings(@NotNull Project project, @NotNull VirtualFile root, boolean localWanted,
                                                 boolean remoteWanted, @Nullable String containingCommit) throws VcsException {
    return GitBranchUtil.convertBranchesToNames(list(project, root, localWanted, remoteWanted, containingCommit));
  }
  /**
   * List branches containing a commit. Specify null if no commit filtering is needed.
   */
  @NotNull
  private static Collection<GitBranch> list(@NotNull Project project, @NotNull VirtualFile root, boolean localWanted, boolean remoteWanted,
                                           @Nullable String containingCommit) throws VcsException {
    // preparing native command executor
    final GitSimpleHandler handler = new GitSimpleHandler(project, root, GitCommand.BRANCH);
    handler.setNoSSH(true);
    handler.setSilent(true);
    handler.addParameters("--no-color");
    boolean remoteOnly = false;
    if (remoteWanted && localWanted) {
      handler.addParameters("-a");
      remoteOnly = false;
    } else if (remoteWanted) {
      handler.addParameters("-r");
      remoteOnly = true;
    }
    if (containingCommit != null) {
      handler.addParameters("--contains", containingCommit);
    }
    final String output = handler.run();

    if (output.trim().length() == 0) {
      // the case after git init and before first commit - there is no branch and no output, and we'll take refs/heads/master
      String head;
      try {
        head = FileUtil.loadFile(new File(root.getPath(), GitRepositoryFiles.GIT_HEAD), GitUtil.UTF8_ENCODING).trim();
        final String prefix = "ref: refs/heads/";
        return head.startsWith(prefix) ?
               Collections.singletonList(new GitBranch(head.substring(prefix.length()), GitBranch.DUMMY_HASH, false)) :
               null;
      } catch (IOException e) {
        LOG.info(e);
        return null;
      }
    }

    Collection<GitBranch> branches = new ArrayList<GitBranch>();
    // standard situation. output example:
    //  master
    //* my_feature
    //  remotes/origin/HEAD -> origin/master
    //  remotes/origin/eap
    //  remotes/origin/feature
    //  remotes/origin/master
    // also possible:
    //* (no branch)
    // and if we call with -r instead of -a, remotes/ prefix is omitted:
    // origin/HEAD -> origin/master
    final String[] split = output.split("\n");
    for (String b : split) {
      boolean current = b.charAt(0) == '*';
      b = b.substring(2).trim();
      if (b.equals(NO_BRANCH_NAME)) { continue; }

      String remotePrefix = null;
      if (b.startsWith("remotes/")) {
        remotePrefix = "remotes/";
      } else if (b.startsWith(GitBranch.REFS_REMOTES_PREFIX)) {
        remotePrefix = GitBranch.REFS_REMOTES_PREFIX;
      }
      boolean isRemote = remotePrefix != null || remoteOnly;
      if (isRemote) {
        if (! remoteOnly) {
          b = b.substring(remotePrefix.length());
        }
        final int idx = b.indexOf("HEAD ->");
        if (idx > 0) {
          continue;
        }
      }
      final GitBranch branch = new GitBranch(b, GitBranch.DUMMY_HASH, isRemote);
      if ((isRemote && remoteWanted) || (!isRemote && localWanted)) {
        branches.add(branch);
      }
    }
    return branches;
  }


}
