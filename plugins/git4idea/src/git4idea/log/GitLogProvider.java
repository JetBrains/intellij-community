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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogSorter;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.GitLocalBranch;
import git4idea.GitRemoteBranch;
import git4idea.GitUserRegistry;
import git4idea.GitVcs;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.GitCommand;
import git4idea.commands.GitSimpleHandler;
import git4idea.history.GitHistoryUtils;
import git4idea.history.GitLogParser;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitLogProvider implements VcsLogProvider {

  private static final Logger LOG = Logger.getInstance(GitLogProvider.class);

  @NotNull private final Project myProject;
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final GitUserRegistry myUserRegistry;
  @NotNull private final VcsLogRefManager myRefSorter;
  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;

  public GitLogProvider(@NotNull Project project, @NotNull GitRepositoryManager repositoryManager, @NotNull VcsLogObjectsFactory factory,
                        @NotNull GitUserRegistry userRegistry) {
    myProject = project;
    myRepositoryManager = repositoryManager;
    myUserRegistry = userRegistry;
    myRefSorter = new GitRefManager(myRepositoryManager);
    myVcsObjectsFactory = factory;
  }

  @NotNull
  @Override
  public List<? extends VcsCommitMetadata> readFirstBlock(@NotNull VirtualFile root, @NotNull Requirements requirements) throws VcsException {
    if (!isRepositoryReady(root)) {
      return Collections.emptyList();
    }

    // need to query more to sort them manually; this doesn't affect performance: it is equal for -1000 and -2000
    int commitCount = requirements.getCommitCount() * 2;

    String[] params = new String[]{"HEAD", "--branches", "--remotes", "--max-count=" + commitCount};
    // NB: not specifying --tags, because it introduces great slowdown if there are many tags,
    // but makes sense only if there are heads without branch or HEAD labels (rare case). Such cases are partially handles below.
    List<VcsCommitMetadata> firstBlock = GitHistoryUtils.loadMetadata(myProject, root, params);

    if (requirements instanceof VcsLogProviderRequirementsEx) {
      VcsLogProviderRequirementsEx rex = (VcsLogProviderRequirementsEx)requirements;
      // on refresh: get new tags, which point to commits not from the first block; then get history, walking down just from these tags
      // on init: just ignore such tagged-only branches. The price for speed-up.
      if (rex.isRefresh()) {
        Collection<VcsRef> newTags = getNewTags(rex.getCurrentRefs(), rex.getPreviousRefs());
        if (!newTags.isEmpty()) {
          final Set<Hash> firstBlockHashes = ContainerUtil.map2Set(firstBlock, new Function<VcsCommitMetadata, Hash>() {
            @Override
            public Hash fun(VcsCommitMetadata metadata) {
              return metadata.getId();
            }
          });
          List<VcsRef> unmatchedHeads = getUnmatchedHeads(firstBlockHashes, newTags);
          if (!unmatchedHeads.isEmpty()) {
            List<VcsCommitMetadata> detailsFromTaggedBranches = loadSomeCommitsOnTaggedBranches(root, commitCount, unmatchedHeads);
            Collection<VcsCommitMetadata> unmatchedCommits = getUnmatchedCommits(firstBlockHashes, detailsFromTaggedBranches);
            firstBlock.addAll(unmatchedCommits);
          }
        }
      }
    }

    firstBlock = VcsLogSorter.sortByDateTopoOrder(firstBlock);
    firstBlock = new ArrayList<VcsCommitMetadata>(firstBlock.subList(0, Math.min(firstBlock.size(), requirements.getCommitCount())));
    return firstBlock;
  }

  @NotNull
  private static Collection<VcsRef> getNewTags(@NotNull Set<VcsRef> currentRefs, @NotNull final Set<VcsRef> previousRefs) {
    return ContainerUtil.filter(currentRefs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return !ref.getType().isBranch() && !previousRefs.contains(ref);
      }
    });
  }

  @NotNull
  private List<VcsCommitMetadata> loadSomeCommitsOnTaggedBranches(@NotNull VirtualFile root, int commitCount,
                                                                  @NotNull List<VcsRef> unmatchedHeads) throws VcsException {
    List<String> params = new ArrayList<String>(ContainerUtil.map(unmatchedHeads, new Function<VcsRef, String>() {
      @Override
      public String fun(VcsRef ref) {
        return ref.getCommitHash().asString();
      }
    }));
    params.add("--max-count=" + commitCount);
    return GitHistoryUtils.loadMetadata(myProject, root, ArrayUtil.toStringArray(params));
  }

  @NotNull
  private static List<VcsRef> getUnmatchedHeads(@NotNull final Set<Hash> firstBlockHashes, @NotNull Collection<VcsRef> refs) {
    return ContainerUtil.filter(refs, new Condition<VcsRef>() {
      @Override
      public boolean value(VcsRef ref) {
        return !firstBlockHashes.contains(ref.getCommitHash());
      }
    });
  }

  @NotNull
  private static Collection<VcsCommitMetadata> getUnmatchedCommits(@NotNull final Set<Hash> firstBlockHashes,
                                                                   @NotNull List<VcsCommitMetadata> detailsFromTaggedBranches) {
    return ContainerUtil.filter(detailsFromTaggedBranches, new Condition<VcsCommitMetadata>() {
      @Override
      public boolean value(VcsCommitMetadata metadata) {
        return !firstBlockHashes.contains(metadata.getId());
      }
    });
  }

  @Override
  public void readAllHashes(@NotNull VirtualFile root, @NotNull Consumer<VcsUser> userRegistry,
                            @NotNull final Consumer<TimedVcsCommit> commitConsumer) throws VcsException {
    if (!isRepositoryReady(root)) {
      return;
    }

    List<String> parameters = new ArrayList<String>(GitHistoryUtils.LOG_ALL);
    parameters.add("--sparse");

    final GitBekParentFixer parentFixer = GitBekParentFixer.prepare(root, this);
    GitHistoryUtils.readCommits(myProject, root, userRegistry, parameters, new Consumer<TimedVcsCommit>() {
      @Override
      public void consume(TimedVcsCommit commit) {
        commitConsumer.consume(parentFixer.fixCommit(commit));
      }
    });
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
      filterParameters.add("--simplify-merges");
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
    return myUserRegistry.getOrReadUser(root);
  }

  @NotNull
  @Override
  public Collection<String> getContainingBranches(@NotNull VirtualFile root, @NotNull Hash commitHash) throws VcsException {
    return GitBranchUtil.getBranches(myProject, root, true, true, commitHash.asString());
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