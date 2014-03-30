/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.config.GitConfigUtil;
import git4idea.history.GitHistoryUtils;
import git4idea.history.GitLogParser;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class GitLogProvider implements VcsLogProvider {

  private static final Logger LOG = Logger.getInstance(GitLogProvider.class);

  @NotNull private final Project myProject;
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final VcsLogRefManager myRefSorter;
  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;

  public GitLogProvider(@NotNull Project project, @NotNull GitRepositoryManager repositoryManager, @NotNull VcsLogObjectsFactory factory) {
    myProject = project;
    myRepositoryManager = repositoryManager;
    myRefSorter = new GitRefManager(myRepositoryManager);
    myVcsObjectsFactory = factory;
  }

  @NotNull
  @Override
  public List<? extends VcsCommitMetadata> readFirstBlock(@NotNull VirtualFile root, boolean ordered, int commitCount) throws VcsException {
    if (!isRepositoryReady(root)) {
      return Collections.emptyList();
    }

    String[] params = ArrayUtil.mergeArrays(ArrayUtil.toStringArray(GitHistoryUtils.LOG_ALL), "--encoding=UTF-8", "--full-history",
                                            "--sparse", "--max-count=" + commitCount);
    if (ordered) {
      params = ArrayUtil.append(params, "--date-order");
    }
    return GitHistoryUtils.loadMetadata(myProject, root, params);
  }

  @NotNull
  @Override
  public List<TimedVcsCommit> readAllHashes(@NotNull VirtualFile root, @NotNull Consumer<VcsUser> userRegistry) throws VcsException {
    if (!isRepositoryReady(root)) {
      return Collections.emptyList();
    }

    return GitHistoryUtils.readCommits(myProject, root, userRegistry, GitHistoryUtils.LOG_ALL);
  }

  @NotNull
  @Override
  public List<? extends VcsShortCommitDetails> readShortDetails(@NotNull VirtualFile root,
                                                                @NotNull List<String> hashes) throws VcsException {
    return GitHistoryUtils.readMiniDetails(myProject, root, hashes);
  }

  @NotNull
  @Override
  public List<? extends VcsFullCommitDetails> readFullDetails(@NotNull VirtualFile root, @NotNull List<String> hashes) throws VcsException {
    return GitHistoryUtils.commitsDetails(myProject, root, hashes);
  }

  @NotNull
  @Override
  public Collection<VcsRef> readAllRefs(@NotNull VirtualFile root) throws VcsException {
    if (!isRepositoryReady(root)) {
      return Collections.emptyList();
    }

    GitRepository repository = getRepository(root);
    repository.update();
    Collection<GitLocalBranch> localBranches = repository.getBranches().getLocalBranches();
    Collection<GitRemoteBranch> remoteBranches = repository.getBranches().getRemoteBranches();
    Collection<VcsRef> refs = new ArrayList<VcsRef>(localBranches.size() + remoteBranches.size());
    for (GitLocalBranch localBranch : localBranches) {
      refs.add(
        myVcsObjectsFactory.createRef(HashImpl.build(localBranch.getHash()), localBranch.getName(), GitRefManager.LOCAL_BRANCH, root));
    }
    for (GitRemoteBranch remoteBranch : remoteBranches) {
      refs.add(myVcsObjectsFactory.createRef(HashImpl.build(remoteBranch.getHash()), remoteBranch.getNameForLocalOperations(),
                                             GitRefManager.REMOTE_BRANCH, root));
    }
    String currentRevision = repository.getCurrentRevision();
    if (currentRevision != null) { // null => fresh repository
      refs.add(myVcsObjectsFactory.createRef(HashImpl.build(currentRevision), "HEAD", GitRefManager.HEAD, root));
    }

    refs.addAll(readTags(root));
    return refs;
  }

  // TODO this is to be removed when tags will be supported by the GitRepositoryReader
  private Collection<? extends VcsRef> readTags(@NotNull VirtualFile root) throws VcsException {
    GitSimpleHandler tagHandler = new GitSimpleHandler(myProject, root, GitCommand.LOG);
    tagHandler.setSilent(true);
    tagHandler.addParameters("--tags", "--no-walk", "--format=%H%d" + GitLogParser.RECORD_START_GIT, "--decorate=full");
    String out = tagHandler.run();
    Collection<VcsRef> refs = new ArrayList<VcsRef>();
    try {
      for (String record : out.split(GitLogParser.RECORD_START)) {
        if (!StringUtil.isEmptyOrSpaces(record)) {
          refs.addAll(new RefParser(myVcsObjectsFactory).parseCommitRefs(record.trim(), root));
        }
      }
    }
    catch (Exception e) {
      LOG.error("Error during tags parsing", new Attachment("stack_trace.txt", ExceptionUtil.getThrowableText(e)),
                new Attachment("git_output.txt", out));
    }
    return refs;
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return GitVcs.getKey();
  }

  @NotNull
  @Override
  public VcsLogRefManager getReferenceManager() {
    return myRefSorter;
  }

  @Override
  public void subscribeToRootRefreshEvents(@NotNull final Collection<VirtualFile> roots, @NotNull final VcsLogRefresher refresher) {
    myProject.getMessageBus().connect(myProject).subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
      @Override
      public void repositoryChanged(@NotNull GitRepository repository) {
        VirtualFile root = repository.getRoot();
        if (roots.contains(root)) {
          refresher.refresh(root);
        }
      }
    });
  }

  @NotNull
  @Override
  public List<TimedVcsCommit> getCommitsMatchingFilter(@NotNull final VirtualFile root,
                                                       @NotNull VcsLogFilterCollection filterCollection,
                                                       int maxCount) throws VcsException {
    if (!isRepositoryReady(root)) {
      return Collections.emptyList();
    }

    List<String> filterParameters = ContainerUtil.newArrayList();

    if (filterCollection.getBranchFilter() != null) {
      GitRepository repository = getRepository(root);
      assert repository != null : "repository is null for root " + root + " but was previously reported as 'ready'";

      boolean atLeastOneBranchExists = false;
      for (String branchName : filterCollection.getBranchFilter().getBranchNames()) {
        if (branchName.equals("HEAD") || repository.getBranches().findBranchByName(branchName) != null) {
          filterParameters.add(branchName);
          atLeastOneBranchExists = true;
        }
      }
      if (!atLeastOneBranchExists) { // no such branches in this repository => filter matches nothing
        return Collections.emptyList();
      }
    }
    else {
      filterParameters.addAll(GitHistoryUtils.LOG_ALL);
    }

    if (filterCollection.getUserFilter() != null) {
      String authorFilter = StringUtil.join(filterCollection.getUserFilter().getUserNames(root), "|");
      filterParameters.add(prepareParameter("author", StringUtil.escapeChar(StringUtil.escapeBackSlashes(authorFilter), '|')));
    }

    if (filterCollection.getDateFilter() != null) {
      // assuming there is only one date filter, until filter expressions are defined
      VcsLogDateFilter filter = filterCollection.getDateFilter();
      if (filter.getAfter() != null) {
        filterParameters.add(prepareParameter("after", filter.getAfter().toString()));
      }
      if (filter.getBefore() != null) {
        filterParameters.add(prepareParameter("before", filter.getBefore().toString()));
      }
    }

    if (filterCollection.getTextFilter() != null) {
      String textFilter = StringUtil.escapeBackSlashes(filterCollection.getTextFilter().getText());
      filterParameters.add(prepareParameter("grep", textFilter));
    }

    filterParameters.add("--regexp-ignore-case"); // affects case sensitivity of any filter (except file filter)
    if (maxCount > 0) {
      filterParameters.add(prepareParameter("max-count", String.valueOf(maxCount)));
    }
    filterParameters.add("--date-order");

    // note: structure filter must be the last parameter, because it uses "--" which separates parameters from paths
    if (filterCollection.getStructureFilter() != null) {
      filterParameters.add("--");
      for (VirtualFile file : filterCollection.getStructureFilter().getFiles(root)) {
        filterParameters.add(file.getPath());
      }
    }

    return GitHistoryUtils.readCommits(myProject, root, Consumer.EMPTY_CONSUMER, filterParameters);
  }

  @Nullable
  @Override
  public VcsUser getCurrentUser(@NotNull VirtualFile root) throws VcsException {
    String userName = GitConfigUtil.getValue(myProject, root, GitConfigUtil.USER_NAME);
    String userEmail = StringUtil.notNullize(GitConfigUtil.getValue(myProject, root, GitConfigUtil.USER_EMAIL));
    return userName == null ? null : myVcsObjectsFactory.createUser(userName, userEmail);
  }

  @NotNull
  @Override
  public Collection<String> getContainingBranches(@NotNull VirtualFile root, @NotNull Hash commitHash) throws VcsException {
    return GitBranchUtil.getBranches(myProject, root, true, true, commitHash.asString());
  }

  @Override
  public boolean supportsFastUnorderedCommits() {
    return true;
  }

  private static String prepareParameter(String paramName, String value) {
    return "--" + paramName + "=" + value; // no value quoting needed, because the parameter itself will be quoted by GeneralCommandLine
  }

  @Nullable
  private GitRepository getRepository(@NotNull VirtualFile root) {
    myRepositoryManager.waitUntilInitialized();
    return myRepositoryManager.getRepositoryForRoot(root);
  }

  private boolean isRepositoryReady(@NotNull VirtualFile root) {
    GitRepository repository = getRepository(root);
    if (repository == null) {
      LOG.error("Repository not found for root " + root);
      return false;
    }
    else if (repository.isFresh()) {
      LOG.info("Fresh repository: " + root);
      return false;
    }
    return true;
  }
}