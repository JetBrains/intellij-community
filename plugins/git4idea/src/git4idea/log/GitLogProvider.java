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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogSorter;
import com.intellij.vcs.log.impl.LogDataImpl;
import com.intellij.vcs.log.impl.HashImpl;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.config.GitVersionSpecialty;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class GitLogProvider implements VcsLogProvider {

  private static final Logger LOG = Logger.getInstance(GitLogProvider.class);
  public static final Function<VcsRef, String> GET_TAG_NAME = new Function<VcsRef, String>() {
    @Override
    public String fun(VcsRef ref) {
      return ref.getType() == GitRefManager.TAG ? ref.getName() : null;
    }
  };
  private static final TObjectHashingStrategy<VcsRef> REF_ONLY_NAME_STRATEGY = new TObjectHashingStrategy<VcsRef>() {
    @Override
    public int computeHashCode(@NotNull VcsRef ref) {
      return ref.getName().hashCode();
    }

    @Override
    public boolean equals(@NotNull VcsRef ref1, @NotNull VcsRef ref2) {
      return ref1.getName().equals(ref2.getName());
    }
  };

  @NotNull private final Project myProject;
  @NotNull private final GitVcs myVcs;
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
    myVcs = ObjectUtils.assertNotNull(GitVcs.getInstance(project));
  }

  @NotNull
  @Override
  public DetailedLogData readFirstBlock(@NotNull VirtualFile root, @NotNull Requirements requirements) throws VcsException {
    if (!isRepositoryReady(root)) {
      return LogDataImpl.empty();
    }
    GitRepository repository = ObjectUtils.assertNotNull(myRepositoryManager.getRepositoryForRoot(root));

    // need to query more to sort them manually; this doesn't affect performance: it is equal for -1000 and -2000
    int commitCount = requirements.getCommitCount() * 2;

    String[] params = new String[]{"HEAD", "--branches", "--remotes", "--max-count=" + commitCount};
    // NB: not specifying --tags, because it introduces great slowdown if there are many tags,
    // but makes sense only if there are heads without branch or HEAD labels (rare case). Such cases are partially handled below.

    boolean refresh = requirements instanceof VcsLogProviderRequirementsEx && ((VcsLogProviderRequirementsEx)requirements).isRefresh();

    DetailedLogData data = GitHistoryUtils.loadMetadata(myProject, root, refresh, params);

    Set<VcsRef> safeRefs = data.getRefs();
    Set<VcsRef> allRefs = new OpenTHashSet<VcsRef>(safeRefs, REF_ONLY_NAME_STRATEGY);
    addNewElements(allRefs, readBranches(repository));

    Collection<VcsCommitMetadata> allDetails;
    if (!refresh) {
      allDetails = data.getCommits();
    }
    else {
      // on refresh: get new tags, which point to commits not from the first block; then get history, walking down just from these tags
      // on init: just ignore such tagged-only branches. The price for speed-up.
      VcsLogProviderRequirementsEx rex = (VcsLogProviderRequirementsEx)requirements;

      Set<String> currentTags = readCurrentTagNames(root);
      addOldStillExistingTags(allRefs, currentTags, rex.getPreviousRefs());

      allDetails = ContainerUtil.newHashSet(data.getCommits());

      Set<String> previousTags = new HashSet<String>(ContainerUtil.mapNotNull(rex.getPreviousRefs(), GET_TAG_NAME));
      Set<String> safeTags = new HashSet<String>(ContainerUtil.mapNotNull(safeRefs, GET_TAG_NAME));
      Set<String> newUnmatchedTags = remove(currentTags, previousTags, safeTags);

      if (!newUnmatchedTags.isEmpty()) {
        DetailedLogData commitsFromTags = loadSomeCommitsOnTaggedBranches(root, commitCount, newUnmatchedTags);
        addNewElements(allDetails, commitsFromTags.getCommits());
        addNewElements(allRefs, commitsFromTags.getRefs());
      }
    }

    List<VcsCommitMetadata> sortedCommits = VcsLogSorter.sortByDateTopoOrder(allDetails);
    sortedCommits = sortedCommits.subList(0, Math.min(sortedCommits.size(), requirements.getCommitCount()));

    return new LogDataImpl(allRefs, sortedCommits);
  }

  private static void addOldStillExistingTags(@NotNull Set<VcsRef> allRefs,
                                              @NotNull Set<String> currentTags,
                                              @NotNull Set<VcsRef> previousRefs) {
    for (VcsRef ref : previousRefs) {
      if (!allRefs.contains(ref) && currentTags.contains(ref.getName())) {
        allRefs.add(ref);
      }
    }
  }

  @NotNull
  private Set<String> readCurrentTagNames(@NotNull VirtualFile root) throws VcsException {
    Set<String> tags = ContainerUtil.newHashSet();
    GitTag.listAsStrings(myProject, root, tags, null);
    return tags;
  }

  @NotNull
  private static <T> Set<T> remove(@NotNull Set<T> original, @NotNull Set<T>... toRemove) {
    Set<T> result = ContainerUtil.newHashSet(original);
    for (Set<T> set : toRemove) {
      result.removeAll(set);
    }
    return result;
  }

  private static <T> void addNewElements(@NotNull Collection<T> original, @NotNull Collection<T> toAdd) {
    for (T item : toAdd) {
      if (!original.contains(item)) {
        original.add(item);
      }
    }
  }

  @NotNull
  private DetailedLogData loadSomeCommitsOnTaggedBranches(@NotNull VirtualFile root, int commitCount,
                                                          @NotNull Collection<String> unmatchedTags) throws VcsException {
    List<String> params = new ArrayList<String>();
    params.add("--max-count=" + commitCount);
    params.addAll(unmatchedTags);
    return GitHistoryUtils.loadMetadata(myProject, root, true, ArrayUtil.toStringArray(params));
  }

  @Override
  @NotNull
  public LogData readAllHashes(@NotNull VirtualFile root, @NotNull final Consumer<TimedVcsCommit> commitConsumer) throws VcsException {
    if (!isRepositoryReady(root)) {
      return LogDataImpl.empty();
    }

    List<String> parameters = new ArrayList<String>(GitHistoryUtils.LOG_ALL);
    parameters.add("--sparse");

    final GitBekParentFixer parentFixer = GitBekParentFixer.prepare(root, this);
    Set<VcsUser> userRegistry = ContainerUtil.newHashSet();
    Set<VcsRef> refs = ContainerUtil.newHashSet();
    GitHistoryUtils.readCommits(myProject, root, parameters, new CollectConsumer<VcsUser>(userRegistry),
                                new CollectConsumer<VcsRef>(refs), new Consumer<TimedVcsCommit>() {
      @Override
      public void consume(TimedVcsCommit commit) {
        commitConsumer.consume(parentFixer.fixCommit(commit));
      }
    });
    return new LogDataImpl(refs, userRegistry);
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
    String noWalk = GitVersionSpecialty.NO_WALK_UNSORTED.existsIn(myVcs.getVersion()) ? "--no-walk=unsorted" : "--no-walk";
    List<String> params = new ArrayList<String>();
    params.add(noWalk);
    params.addAll(hashes);
    return GitHistoryUtils.history(myProject, root, ArrayUtil.toStringArray(params));
  }

  @NotNull
  private Set<VcsRef> readBranches(@NotNull GitRepository repository) {
    VirtualFile root = repository.getRoot();
    Collection<GitLocalBranch> localBranches = repository.getBranches().getLocalBranches();
    Collection<GitRemoteBranch> remoteBranches = repository.getBranches().getRemoteBranches();
    Set<VcsRef> refs = new HashSet<VcsRef>(localBranches.size() + remoteBranches.size());
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
      String textFilter = filterCollection.getTextFilter().getText();
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

    List<TimedVcsCommit> commits = ContainerUtil.newArrayList();
    GitHistoryUtils.readCommits(myProject, root, filterParameters, Consumer.EMPTY_CONSUMER, Consumer.EMPTY_CONSUMER,
                                new CollectConsumer<TimedVcsCommit>(commits));
    return commits;
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