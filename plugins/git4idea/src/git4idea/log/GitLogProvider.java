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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableNotNullFunction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.OpenTHashSet;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogSorter;
import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.LogDataImpl;
import com.intellij.vcs.log.util.StopWatch;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBranchesCollection;
import git4idea.config.GitVersionSpecialty;
import git4idea.history.GitHistoryUtils;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import git4idea.repo.GitRepositoryManager;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class GitLogProvider implements VcsLogProvider {

  private static final Logger LOG = Logger.getInstance(GitLogProvider.class);
  public static final Function<VcsRef, String> GET_TAG_NAME = new Function<VcsRef, String>() {
    @Override
    public String fun(VcsRef ref) {
      return ref.getType() == GitRefManager.TAG ? ref.getName() : null;
    }
  };
  public static final TObjectHashingStrategy<VcsRef> DONT_CONSIDER_SHA = new TObjectHashingStrategy<VcsRef>() {
    @Override
    public int computeHashCode(@NotNull VcsRef ref) {
      return 31 * ref.getName().hashCode() + ref.getType().hashCode();
    }

    @Override
    public boolean equals(@NotNull VcsRef ref1, @NotNull VcsRef ref2) {
      return ref1.getName().equals(ref2.getName()) && ref1.getType().equals(ref2.getType());
    }
  };

  @NotNull private final Project myProject;
  @NotNull private final GitVcs myVcs;
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final GitUserRegistry myUserRegistry;
  @NotNull private final VcsLogRefManager myRefSorter;
  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;

  public GitLogProvider(@NotNull Project project,
                        @NotNull GitRepositoryManager repositoryManager,
                        @NotNull VcsLogObjectsFactory factory,
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

    DetailedLogData data = GitHistoryUtils.loadMetadata(myProject, root, true, params);

    Set<VcsRef> safeRefs = data.getRefs();
    Set<VcsRef> allRefs = new OpenTHashSet<>(safeRefs, DONT_CONSIDER_SHA);
    Set<VcsRef> branches = readBranches(repository);
    addNewElements(allRefs, branches);

    Collection<VcsCommitMetadata> allDetails;
    Set<String> currentTagNames = null;
    DetailedLogData commitsFromTags = null;
    if (!refresh) {
      allDetails = data.getCommits();
    }
    else {
      // on refresh: get new tags, which point to commits not from the first block; then get history, walking down just from these tags
      // on init: just ignore such tagged-only branches. The price for speed-up.
      VcsLogProviderRequirementsEx rex = (VcsLogProviderRequirementsEx)requirements;

      currentTagNames = readCurrentTagNames(root);
      addOldStillExistingTags(allRefs, currentTagNames, rex.getPreviousRefs());

      allDetails = newHashSet(data.getCommits());

      Set<String> previousTags = newHashSet(ContainerUtil.mapNotNull(rex.getPreviousRefs(), GET_TAG_NAME));
      Set<String> safeTags = newHashSet(ContainerUtil.mapNotNull(safeRefs, GET_TAG_NAME));
      Set<String> newUnmatchedTags = remove(currentTagNames, previousTags, safeTags);

      if (!newUnmatchedTags.isEmpty()) {
        commitsFromTags = loadSomeCommitsOnTaggedBranches(root, commitCount, newUnmatchedTags);
        addNewElements(allDetails, commitsFromTags.getCommits());
        addNewElements(allRefs, commitsFromTags.getRefs());
      }
    }

    StopWatch sw = StopWatch.start("sorting commits in " + root.getName());
    List<VcsCommitMetadata> sortedCommits = VcsLogSorter.sortByDateTopoOrder(allDetails);
    sortedCommits = sortedCommits.subList(0, Math.min(sortedCommits.size(), requirements.getCommitCount()));
    sw.report();

    if (LOG.isDebugEnabled()) {
      validateDataAndReportError(root, allRefs, sortedCommits, data, branches, currentTagNames, commitsFromTags);
    }

    return new LogDataImpl(allRefs, sortedCommits);
  }

  private static void validateDataAndReportError(@NotNull final VirtualFile root,
                                                 @NotNull final Set<VcsRef> allRefs,
                                                 @NotNull final List<VcsCommitMetadata> sortedCommits,
                                                 @NotNull final DetailedLogData firstBlockSyncData,
                                                 @NotNull final Set<VcsRef> manuallyReadBranches,
                                                 @Nullable final Set<String> currentTagNames,
                                                 @Nullable final DetailedLogData commitsFromTags) {
    StopWatch sw = StopWatch.start("validating data in " + root.getName());
    final Set<Hash> refs = ContainerUtil.map2Set(allRefs, new Function<VcsRef, Hash>() {
      @Override
      public Hash fun(VcsRef ref) {
        return ref.getCommitHash();
      }
    });

    PermanentGraphImpl.newInstance(sortedCommits, new GraphColorManager<Hash>() {
      @Override
      public int getColorOfBranch(@NotNull Hash headCommit) {
        return 0;
      }

      @Override
      public int getColorOfFragment(@NotNull Hash headCommit, int magicIndex) {
        return 0;
      }

      @Override
      public int compareHeads(@NotNull Hash head1, @NotNull Hash head2) {
        if (!refs.contains(head1) || !refs.contains(head2)) {
          LOG.error("GitLogProvider returned inconsistent data", new Attachment("error-details.txt",
                                                                                printErrorDetails(root, allRefs, sortedCommits,
                                                                                                  firstBlockSyncData, manuallyReadBranches,
                                                                                                  currentTagNames, commitsFromTags)));
        }
        return 0;
      }
    }, refs);
    sw.report();
  }

  @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
  private static String printErrorDetails(@NotNull VirtualFile root,
                                          @NotNull Set<VcsRef> allRefs,
                                          @NotNull List<VcsCommitMetadata> sortedCommits,
                                          @NotNull DetailedLogData firstBlockSyncData,
                                          @NotNull Set<VcsRef> manuallyReadBranches,
                                          @Nullable Set<String> currentTagNames,
                                          @Nullable DetailedLogData commitsFromTags) {

    StringBuilder sb = new StringBuilder();
    sb.append("[" + root.getName() + "]\n");
    sb.append("First block data from Git:\n");
    sb.append(printLogData(firstBlockSyncData));
    sb.append("\n\nManually read refs:\n");
    sb.append(printRefs(manuallyReadBranches));
    sb.append("\n\nCurrent tag names:\n");
    if (currentTagNames != null) {
      sb.append(StringUtil.join(currentTagNames, ", "));
      if (commitsFromTags != null) {
        sb.append(printLogData(commitsFromTags));
      }
      else {
        sb.append("\n\nCommits from new tags were not read.\n");
      }
    }
    else {
      sb.append("\n\nCurrent tags were not read\n");
    }

    sb.append("\n\nResult:\n");
    sb.append("\nCommits (last 100): \n");
    sb.append(printCommits(sortedCommits));
    sb.append("\nAll refs:\n");
    sb.append(printRefs(allRefs));
    return sb.toString();
  }

  @NotNull
  private static String printLogData(@NotNull DetailedLogData firstBlockSyncData) {
    return String
      .format("Last 100 commits:\n%s\nRefs:\n%s", printCommits(firstBlockSyncData.getCommits()), printRefs(firstBlockSyncData.getRefs()));
  }

  @NotNull
  private static String printCommits(@NotNull List<VcsCommitMetadata> commits) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < Math.min(commits.size(), 100); i++) {
      GraphCommit<Hash> commit = commits.get(i);
      sb.append(
        String.format("%s -> %s\n", commit.getId().toShortString(), StringUtil.join(commit.getParents(), new Function<Hash, String>() {
          @Override
          public String fun(Hash hash) {
            return hash.toShortString();
          }
        }, ", ")));
    }
    return sb.toString();
  }

  @NotNull
  private static String printRefs(@NotNull Set<VcsRef> refs) {
    return StringUtil.join(refs, new Function<VcsRef, String>() {
      @Override
      public String fun(VcsRef ref) {
        return ref.getCommitHash().toShortString() + " : " + ref.getName();
      }
    }, "\n");
  }

  private static void addOldStillExistingTags(@NotNull Set<VcsRef> allRefs,
                                              @NotNull Set<String> currentTags,
                                              @NotNull Collection<VcsRef> previousRefs) {
    for (VcsRef ref : previousRefs) {
      if (!allRefs.contains(ref) && currentTags.contains(ref.getName())) {
        allRefs.add(ref);
      }
    }
  }

  @NotNull
  private Set<String> readCurrentTagNames(@NotNull VirtualFile root) throws VcsException {
    StopWatch sw = StopWatch.start("reading tags in " + root.getName());
    Set<String> tags = newHashSet();
    GitTag.listAsStrings(myProject, root, tags, null);
    sw.report();
    return tags;
  }

  @NotNull
  private static <T> Set<T> remove(@NotNull Set<T> original, @NotNull Set<T>... toRemove) {
    Set<T> result = newHashSet(original);
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
  private DetailedLogData loadSomeCommitsOnTaggedBranches(@NotNull VirtualFile root,
                                                          int commitCount,
                                                          @NotNull Collection<String> unmatchedTags) throws VcsException {
    StopWatch sw = StopWatch.start("loading commits on tagged branch in " + root.getName());
    List<String> params = new ArrayList<>();
    params.add("--max-count=" + commitCount);
    params.addAll(unmatchedTags);
    sw.report();
    return GitHistoryUtils.loadMetadata(myProject, root, true, ArrayUtil.toStringArray(params));
  }

  @Override
  @NotNull
  public LogData readAllHashes(@NotNull VirtualFile root, @NotNull final Consumer<TimedVcsCommit> commitConsumer) throws VcsException {
    if (!isRepositoryReady(root)) {
      return LogDataImpl.empty();
    }

    List<String> parameters = new ArrayList<>(GitHistoryUtils.LOG_ALL);
    parameters.add("--date-order");

    final GitBekParentFixer parentFixer = GitBekParentFixer.prepare(root, this);
    Set<VcsUser> userRegistry = newHashSet();
    Set<VcsRef> refs = newHashSet();
    GitHistoryUtils.readCommits(myProject, root, parameters, new CollectConsumer<>(userRegistry), new CollectConsumer<>(refs),
                                new Consumer<TimedVcsCommit>() {
                                  @Override
                                  public void consume(TimedVcsCommit commit) {
                                    commitConsumer.consume(parentFixer.fixCommit(commit));
                                  }
                                });
    return new LogDataImpl(refs, userRegistry);
  }

  @NotNull
  @Override
  public List<? extends VcsShortCommitDetails> readShortDetails(@NotNull final VirtualFile root, @NotNull List<String> hashes)
    throws VcsException {
    return VcsFileUtil
      .foreachChunk(hashes, new ThrowableNotNullFunction<List<String>, List<? extends VcsShortCommitDetails>, VcsException>() {
        @NotNull
        @Override
        public List<? extends VcsShortCommitDetails> fun(@NotNull List<String> hashes) throws VcsException {
          return GitHistoryUtils.readMiniDetails(myProject, root, hashes);
        }
      });
  }

  @NotNull
  @Override
  public List<? extends VcsFullCommitDetails> readFullDetails(@NotNull final VirtualFile root, @NotNull List<String> hashes)
    throws VcsException {
    return VcsFileUtil
      .foreachChunk(hashes, new ThrowableNotNullFunction<List<String>, List<? extends VcsFullCommitDetails>, VcsException>() {
        @NotNull
        @Override
        public List<? extends VcsFullCommitDetails> fun(@NotNull List<String> hashes) throws VcsException {
          String noWalk = GitVersionSpecialty.NO_WALK_UNSORTED.existsIn(myVcs.getVersion()) ? "--no-walk=unsorted" : "--no-walk";
          List<String> params = new ArrayList<>();
          params.add(noWalk);
          params.addAll(hashes);

          return GitHistoryUtils.history(myProject, root, ArrayUtil.toStringArray(params));
        }
      });
  }

  @NotNull
  private Set<VcsRef> readBranches(@NotNull GitRepository repository) {
    StopWatch sw = StopWatch.start("readBranches in " + repository.getRoot().getName());
    VirtualFile root = repository.getRoot();
    repository.update();
    GitBranchesCollection branches = repository.getBranches();
    Collection<GitLocalBranch> localBranches = branches.getLocalBranches();
    Collection<GitRemoteBranch> remoteBranches = branches.getRemoteBranches();
    Set<VcsRef> refs = new THashSet<>(localBranches.size() + remoteBranches.size());
    for (GitLocalBranch localBranch : localBranches) {
      Hash hash = branches.getHash(localBranch);
      assert hash != null;
      refs.add(myVcsObjectsFactory.createRef(hash, localBranch.getName(), GitRefManager.LOCAL_BRANCH, root));
    }
    for (GitRemoteBranch remoteBranch : remoteBranches) {
      Hash hash = branches.getHash(remoteBranch);
      assert hash != null;
      refs.add(myVcsObjectsFactory.createRef(hash, remoteBranch.getNameForLocalOperations(), GitRefManager.REMOTE_BRANCH, root));
    }
    String currentRevision = repository.getCurrentRevision();
    if (currentRevision != null) { // null => fresh repository
      refs.add(myVcsObjectsFactory.createRef(HashImpl.build(currentRevision), "HEAD", GitRefManager.HEAD, root));
    }
    sw.report();
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

  @NotNull
  @Override
  public Disposable subscribeToRootRefreshEvents(@NotNull final Collection<VirtualFile> roots, @NotNull final VcsLogRefresher refresher) {
    MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, new GitRepositoryChangeListener() {
      @Override
      public void repositoryChanged(@NotNull GitRepository repository) {
        VirtualFile root = repository.getRoot();
        if (roots.contains(root)) {
          refresher.refresh(root);
        }
      }
    });
    return connection;
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

    VcsLogBranchFilter branchFilter = filterCollection.getBranchFilter();
    if (branchFilter != null) {
      GitRepository repository = getRepository(root);
      assert repository != null : "repository is null for root " + root + " but was previously reported as 'ready'";

      Collection<GitBranch> branches = ContainerUtil
        .newArrayList(ContainerUtil.concat(repository.getBranches().getLocalBranches(), repository.getBranches().getRemoteBranches()));
      Collection<String> branchNames = GitBranchUtil.convertBranchesToNames(branches);
      Collection<String> predefinedNames = ContainerUtil.list("HEAD");

      boolean atLeastOneBranchExists = false;
      for (String branchName : ContainerUtil.concat(branchNames, predefinedNames)) {
        if (branchFilter.matches(branchName)) {
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
      List<String> authors = ContainerUtil.map(filterCollection.getUserFilter().getUserNames(root), UserNameRegex.BASIC_INSTANCE);
      if (GitVersionSpecialty.LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR.existsIn(myVcs.getVersion())) {
        filterParameters.add(prepareParameter("author", StringUtil.join(authors, "\\|")));
      } else {
        filterParameters.addAll(authors.stream().map(a -> prepareParameter("author", a)).collect(Collectors.toList()));
      }
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
      filterParameters.add(prepareParameter("grep", StringUtil.escapeChars(textFilter, '[', ']')));
    }

    filterParameters.add("--regexp-ignore-case"); // affects case sensitivity of any filter (except file filter)
    if (maxCount > 0) {
      filterParameters.add(prepareParameter("max-count", String.valueOf(maxCount)));
    }

    // note: structure filter must be the last parameter, because it uses "--" which separates parameters from paths
    if (filterCollection.getStructureFilter() != null) {
      Collection<FilePath> files = filterCollection.getStructureFilter().getFiles();
      if (!files.isEmpty()) {
        filterParameters.add("--full-history");
        filterParameters.add("--simplify-merges");
        filterParameters.add("--");
        for (FilePath file : files) {
          filterParameters.add(file.getPath());
        }
      }
    }

    List<TimedVcsCommit> commits = ContainerUtil.newArrayList();
    GitHistoryUtils.readCommits(myProject, root, filterParameters, EmptyConsumer.<VcsUser>getInstance(),
                                EmptyConsumer.<VcsRef>getInstance(), new CollectConsumer<>(commits));
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

  @Nullable
  @Override
  public String getCurrentBranch(@NotNull VirtualFile root) {
    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) return null;
    String currentBranchName = repository.getCurrentBranchName();
    if (currentBranchName == null && repository.getCurrentRevision() != null) {
      return "HEAD";
    }
    return currentBranchName;
  }

  @Nullable
  @Override
  public <T> T getPropertyValue(VcsLogProperties.VcsLogProperty<T> property) {
    if (property == VcsLogProperties.LIGHTWEIGHT_BRANCHES) {
      return (T)Boolean.TRUE;
    }
    return null;
  }

  private static String prepareParameter(String paramName, String value) {
    return "--" + paramName + "=" + value; // no value quoting needed, because the parameter itself will be quoted by GeneralCommandLine
  }

  @Nullable
  private GitRepository getRepository(@NotNull VirtualFile root) {
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

  @NotNull
  private static <T> Set<T> newHashSet() {
    return new THashSet<>();
  }

  @NotNull
  private static <T> Set<T> newHashSet(@NotNull Collection<T> initialCollection) {
    return new THashSet<>(initialCollection);
  }
}