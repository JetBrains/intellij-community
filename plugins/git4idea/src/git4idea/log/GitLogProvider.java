// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.VcsLogSorter;
import com.intellij.vcs.log.graph.GraphColorManager;
import com.intellij.vcs.log.graph.GraphCommit;
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.impl.LogDataImpl;
import com.intellij.vcs.log.impl.VcsIndexableLogProvider;
import com.intellij.vcs.log.impl.VcsLogIndexer;
import com.intellij.vcs.log.util.StopWatch;
import com.intellij.vcs.log.util.UserNameRegex;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcs.log.visible.filters.VcsLogFiltersKt;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBranchesCollection;
import git4idea.commands.Git;
import git4idea.config.GitVersionSpecialty;
import git4idea.history.GitCommitRequirements;
import git4idea.history.GitCommitRequirements.DiffInMergeCommits;
import git4idea.history.GitLogHistoryHandler;
import git4idea.history.GitLogUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.repo.GitSubmodule;
import git4idea.repo.GitSubmoduleKt;
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.vcs.log.VcsLogFilterCollection.*;
import static git4idea.history.GitCommitRequirements.DiffRenameLimit;

public final class GitLogProvider implements VcsLogProvider, VcsIndexableLogProvider {
  private static final Logger LOG = Logger.getInstance(GitLogProvider.class);
  public static final Function<VcsRef, String> GET_TAG_NAME = ref -> ref.getType() == GitRefManager.TAG ? ref.getName() : null;
  public static final it.unimi.dsi.fastutil.Hash.Strategy<VcsRef> DONT_CONSIDER_SHA = new it.unimi.dsi.fastutil.Hash.Strategy<>() {
    @Override
    public int hashCode(@Nullable VcsRef ref) {
      if (ref == null) {
        return 0;
      }
      return 31 * ref.getName().hashCode() + ref.getType().hashCode();
    }

    @Override
    public boolean equals(@Nullable VcsRef ref1, @Nullable VcsRef ref2) {
      return ref1 == ref2 || (ref1 != null && ref2 != null && ref1.getName().equals(ref2.getName()) && ref1.getType().equals(ref2.getType()));
    }
  };

  @NotNull private final Project myProject;
  @NotNull private final GitVcs myVcs;
  @NotNull private final GitRepositoryManager myRepositoryManager;
  @NotNull private final VcsLogRefManager myRefSorter;
  @NotNull private final VcsLogObjectsFactory myVcsObjectsFactory;

  public GitLogProvider(@NotNull Project project) {
    myProject = project;
    myRepositoryManager = GitRepositoryManager.getInstance(project);
    myRefSorter = new GitRefManager(myProject, myRepositoryManager);
    myVcsObjectsFactory = project.getService(VcsLogObjectsFactory.class);
    myVcs = GitVcs.getInstance(project);
  }

  @NotNull
  @Override
  public DetailedLogData readFirstBlock(@NotNull VirtualFile root, @NotNull Requirements requirements) throws VcsException {
    if (!isRepositoryReady(root)) {
      return LogDataImpl.empty();
    }
    GitRepository repository = Objects.requireNonNull(myRepositoryManager.getRepositoryForRoot(root));

    // need to query more to sort them manually; this doesn't affect performance: it is equal for -1000 and -2000
    int commitCount = requirements.getCommitCount() * 2;

    String[] params = new String[]{GitUtil.HEAD, "--branches", "--remotes", "--max-count=" + commitCount};
    // NB: not specifying --tags, because it introduces great slowdown if there are many tags,
    // but makes sense only if there are heads without branch or HEAD labels (rare case). Such cases are partially handled below.

    boolean refresh = requirements instanceof VcsLogProviderRequirementsEx && ((VcsLogProviderRequirementsEx)requirements).isRefresh();

    DetailedLogData data = GitLogUtil.collectMetadata(myProject, root, params);

    Set<VcsRef> safeRefs = data.getRefs();
    Set<VcsRef> allRefs = new ObjectOpenCustomHashSet<>(safeRefs, DONT_CONSIDER_SHA);
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

      allDetails = new HashSet<>(data.getCommits());

      Set<String> previousTags = new HashSet<>(ContainerUtil.mapNotNull(rex.getPreviousRefs(), GET_TAG_NAME));
      Set<String> safeTags = new HashSet<>(ContainerUtil.mapNotNull(safeRefs, GET_TAG_NAME));
      Set<String> newUnmatchedTags = remove(currentTagNames, previousTags, safeTags);

      if (!newUnmatchedTags.isEmpty()) {
        commitsFromTags = loadSomeCommitsOnTaggedBranches(root, commitCount, newUnmatchedTags);
        addNewElements(allDetails, commitsFromTags.getCommits());
        addNewElements(allRefs, commitsFromTags.getRefs());
      }
    }

    StopWatch sw = StopWatch.start("sorting commits in " + root.getName());
    List<VcsCommitMetadata> sortedCommits = VcsLogSorter.sortByDateTopoOrder(allDetails);
    sortedCommits = ContainerUtil.getFirstItems(sortedCommits, requirements.getCommitCount());
    sw.report();

    if (LOG.isDebugEnabled()) {
      validateDataAndReportError(root, allRefs, sortedCommits, data, branches, currentTagNames, commitsFromTags);
    }

    return new LogDataImpl(allRefs, GitBekParentFixer.fixCommits(sortedCommits));
  }

  private static void validateDataAndReportError(@NotNull final VirtualFile root,
                                                 @NotNull final Set<? extends VcsRef> allRefs,
                                                 @NotNull final List<? extends VcsCommitMetadata> sortedCommits,
                                                 @NotNull final DetailedLogData firstBlockSyncData,
                                                 @NotNull final Set<? extends VcsRef> manuallyReadBranches,
                                                 @Nullable final Set<String> currentTagNames,
                                                 @Nullable final DetailedLogData commitsFromTags) {
    StopWatch sw = StopWatch.start("validating data in " + root.getName());
    final Set<Hash> refs = ContainerUtil.map2Set(allRefs, VcsRef::getCommitHash);

    PermanentGraphImpl.newInstance(sortedCommits, new GraphColorManager<>() {
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
                                          @NotNull Set<? extends VcsRef> allRefs,
                                          @NotNull List<? extends VcsCommitMetadata> sortedCommits,
                                          @NotNull DetailedLogData firstBlockSyncData,
                                          @NotNull Set<? extends VcsRef> manuallyReadBranches,
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
  private static String printCommits(@NotNull List<? extends VcsCommitMetadata> commits) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < Math.min(commits.size(), 100); i++) {
      GraphCommit<Hash> commit = commits.get(i);
      sb.append(
        String
          .format("%s -> %s\n", commit.getId().toShortString(), StringUtil.join(commit.getParents(), Hash::toShortString, ", ")));
    }
    return sb.toString();
  }

  @NotNull
  private static String printRefs(@NotNull Set<? extends VcsRef> refs) {
    return StringUtil.join(refs, ref -> ref.getCommitHash().toShortString() + " : " + ref.getName(), "\n");
  }

  private static void addOldStillExistingTags(@NotNull Set<? super VcsRef> allRefs,
                                              @NotNull Set<String> currentTags,
                                              @NotNull Collection<? extends VcsRef> previousRefs) {
    for (VcsRef ref : previousRefs) {
      if (!allRefs.contains(ref) && currentTags.contains(ref.getName())) {
        allRefs.add(ref);
      }
    }
  }

  @NotNull
  private Set<String> readCurrentTagNames(@NotNull VirtualFile root) throws VcsException {
    StopWatch sw = StopWatch.start("reading tags in " + root.getName());
    Set<String> tags = new HashSet<>();
    tags.addAll(GitBranchUtil.getAllTags(myProject, root));
    sw.report();
    return tags;
  }

  @NotNull
  private static <T> Set<T> remove(@NotNull Set<? extends T> original, Set<T> @NotNull ... toRemove) {
    Set<T> result = new HashSet<>(original);
    for (Set<T> set : toRemove) {
      result.removeAll(set);
    }
    return result;
  }

  private static <T> void addNewElements(@NotNull Collection<? super T> original, @NotNull Collection<? extends T> toAdd) {
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

    Set<VcsRef> refs = new HashSet<>();
    Set<VcsCommitMetadata> commits = new HashSet<>();
    VcsFileUtil.foreachChunk(new ArrayList<>(unmatchedTags), 1, tagsChunk -> {
      String[] parameters = ArrayUtilRt.toStringArray(ContainerUtil.concat(params, tagsChunk));
      DetailedLogData logData = GitLogUtil.collectMetadata(myProject, root, parameters);
      refs.addAll(logData.getRefs());
      commits.addAll(logData.getCommits());
    });

    sw.report();
    return new LogDataImpl(refs, new ArrayList<>(commits));
  }

  @Override
  @NotNull
  public LogData readAllHashes(@NotNull VirtualFile root, @NotNull final Consumer<? super TimedVcsCommit> commitConsumer)
    throws VcsException {
    if (!isRepositoryReady(root)) {
      return LogDataImpl.empty();
    }

    List<String> parameters = new ArrayList<>(GitLogUtil.LOG_ALL);
    parameters.add("--date-order");

    final GitBekParentFixer parentFixer = GitBekParentFixer.prepare(myProject, root);
    Set<VcsUser> userRegistry = new HashSet<>();
    Set<VcsRef> refs = new HashSet<>();
    GitLogUtil.readTimedCommits(myProject, root, parameters, new CollectConsumer<>(userRegistry), new CollectConsumer<>(refs),
                                commit -> commitConsumer.consume(parentFixer.fixCommit(commit)));
    return new LogDataImpl(refs, userRegistry);
  }

  @Override
  public void readFullDetails(@NotNull VirtualFile root,
                              @NotNull List<String> hashes,
                              @NotNull Consumer<? super VcsFullCommitDetails> commitConsumer)
    throws VcsException {
    if (!isRepositoryReady(root)) {
      return;
    }

    GitCommitRequirements requirements = new GitCommitRequirements(shouldIncludeRootChanges(myRepositoryManager, root),
                                                                   DiffRenameLimit.GitConfig.INSTANCE,
                                                                   DiffInMergeCommits.DIFF_TO_PARENTS);
    GitLogUtil.readFullDetailsForHashes(myProject, root, hashes, requirements, commitConsumer);
  }

  static boolean shouldIncludeRootChanges(@NotNull GitRepositoryManager manager,
                                          @NotNull VirtualFile root) {
    GitRepository repository = manager.getRepositoryForRoot(root);
    if (repository == null) return true;
    return !repository.getInfo().isShallow();
  }

  @Override
  public void readMetadata(@NotNull VirtualFile root, @NotNull List<String> hashes, @NotNull Consumer<? super VcsCommitMetadata> consumer)
    throws VcsException {
    GitLogUtil.collectMetadata(myProject, root, hashes, consumer);
  }

  @NotNull
  private Set<VcsRef> readBranches(@NotNull GitRepository repository) {
    StopWatch sw = StopWatch.start("readBranches in " + repository.getRoot().getName());
    VirtualFile root = repository.getRoot();
    repository.update();
    GitBranchesCollection branches = repository.getBranches();
    Collection<GitLocalBranch> localBranches = branches.getLocalBranches();
    Collection<GitRemoteBranch> remoteBranches = branches.getRemoteBranches();
    Set<VcsRef> refs = new HashSet<>(localBranches.size() + remoteBranches.size());
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
      refs.add(myVcsObjectsFactory.createRef(HashImpl.build(currentRevision), GitUtil.HEAD, GitRefManager.HEAD, root));
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
  public Disposable subscribeToRootRefreshEvents(@NotNull final Collection<? extends VirtualFile> roots,
                                                 @NotNull final VcsLogRefresher refresher) {
    MessageBusConnection connection = myProject.getMessageBus().connect();
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, repository -> {
      VirtualFile root = repository.getRoot();
      if (roots.contains(root)) {
        refresher.refresh(root);
      }
    });
    return connection;
  }

  @NotNull
  @Override
  public List<TimedVcsCommit> getCommitsMatchingFilter(@NotNull final VirtualFile root,
                                                       @NotNull VcsLogFilterCollection filterCollection,
                                                       int maxCount) throws VcsException {
    VcsLogRangeFilter rangeFilter = filterCollection.get(RANGE_FILTER);
    if (rangeFilter == null) {
      return getCommitsMatchingFilter(root, filterCollection, null, maxCount);
    }

     /*
       We expect a "branch + range" combined filter to display the union of commits reachable from the branch,
       and commits belonging to the range. But Git intersects results for such parameters.
       => to solve this, we make a query for branch filters, and then separate queries for each of the ranges.
     */
    Set<TimedVcsCommit> commits = new LinkedHashSet<>();
    if (filterCollection.get(BRANCH_FILTER) != null || filterCollection.get(REVISION_FILTER) != null) {
      commits.addAll(getCommitsMatchingFilter(root, filterCollection, null, maxCount));
      filterCollection = VcsLogFiltersKt.without(VcsLogFiltersKt.without(filterCollection, BRANCH_FILTER), REVISION_FILTER);
    }
    for (VcsLogRangeFilter.RefRange range : rangeFilter.getRanges()) {
      commits.addAll(getCommitsMatchingFilter(root, filterCollection, range, maxCount));
    }
    return new ArrayList<>(commits);
  }

  @NotNull
  private List<TimedVcsCommit> getCommitsMatchingFilter(@NotNull final VirtualFile root,
                                                        @NotNull VcsLogFilterCollection filterCollection,
                                                        @Nullable VcsLogRangeFilter.RefRange range,
                                                        int maxCount) throws VcsException {

    if (!isRepositoryReady(root)) {
      return Collections.emptyList();
    }

    List<String> filterParameters = new ArrayList<>();

    VcsLogBranchFilter branchFilter = filterCollection.get(BRANCH_FILTER);
    VcsLogRevisionFilter revisionFilter = filterCollection.get(REVISION_FILTER);
    if (branchFilter != null || revisionFilter != null || range != null) {
      boolean atLeastOneBranchExists = false;

      if (branchFilter != null) {
        GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
        assert repository != null : "repository is null for root " + root + " but was previously reported as 'ready'";

        Collection<GitBranch> branches = ContainerUtil
          .newArrayList(ContainerUtil.concat(repository.getBranches().getLocalBranches(), repository.getBranches().getRemoteBranches()));
        Collection<String> branchNames = GitBranchUtil.convertBranchesToNames(branches);
        Collection<String> predefinedNames = Collections.singletonList(GitUtil.HEAD);

        for (String branchName : ContainerUtil.concat(branchNames, predefinedNames)) {
          if (branchFilter.matches(branchName)) {
            filterParameters.add(branchName);
            atLeastOneBranchExists = true;
          }
        }
      }
      if (revisionFilter != null) {
        for (CommitId commit : revisionFilter.getHeads()) {
          if (commit.getRoot().equals(root)) {
            filterParameters.add(commit.getHash().asString());
            atLeastOneBranchExists = true;
          }
        }
      }

      if (range != null) {
        filterParameters.add(range.getExclusiveRef() + ".." + range.getInclusiveRef());
      }

      if (range == null && !atLeastOneBranchExists) { // no such branches in this repository => filter matches nothing
        return Collections.emptyList();
      }
    }
    else {
      filterParameters.addAll(GitLogUtil.LOG_ALL);
    }

    VcsLogDateFilter dateFilter = filterCollection.get(DATE_FILTER);
    if (dateFilter != null) {
      // assuming there is only one date filter, until filter expressions are defined
      if (dateFilter.getAfter() != null) {
        filterParameters.add(prepareParameter("after", dateFilter.getAfter().toString()));
      }
      if (dateFilter.getBefore() != null) {
        filterParameters.add(prepareParameter("before", dateFilter.getBefore().toString()));
      }
    }

    VcsLogTextFilter textFilter = filterCollection.get(TEXT_FILTER);
    String text = textFilter != null ? textFilter.getText() : null;
    boolean regexp = textFilter == null || textFilter.isRegex();
    boolean caseSensitive = textFilter != null && textFilter.matchesCase();
    appendTextFilterParameters(text, regexp, caseSensitive, filterParameters);

    VcsLogUserFilter userFilter = filterCollection.get(USER_FILTER);
    if (userFilter != null) {
      Collection<String> names = ContainerUtil.map(userFilter.getUsers(root), VcsUserUtil::toExactString);
      if (regexp) {
        List<String> authors = ContainerUtil.map(names, UserNameRegex.EXTENDED_INSTANCE);
        if (GitVersionSpecialty.LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR.existsIn(myProject)) {
          filterParameters.add(prepareParameter("author", StringUtil.join(authors, "|")));
        }
        else {
          filterParameters.addAll(ContainerUtil.map(authors, a -> prepareParameter("author", a)));
        }
      }
      else {
        filterParameters.addAll(ContainerUtil.map(names, a -> prepareParameter("author", StringUtil.escapeBackSlashes(a))));
      }
    }

    if (maxCount > 0) {
      filterParameters.add(prepareParameter("max-count", String.valueOf(maxCount)));
    }

    // note: structure filter must be the last parameter, because it uses "--" which separates parameters from paths
    VcsLogStructureFilter structureFilter = filterCollection.get(STRUCTURE_FILTER);
    if (structureFilter != null) {
      Collection<FilePath> files = structureFilter.getFiles();
      if (!files.isEmpty()) {
        filterParameters.add("--full-history");
        filterParameters.add("--simplify-merges");
        filterParameters.add("--");
        for (FilePath file : files) {
          filterParameters.add(VcsFileUtil.relativePath(root, file));
        }
      }
    }

    List<TimedVcsCommit> commits = new ArrayList<>();
    GitLogUtil.readTimedCommits(myProject, root, filterParameters, EmptyConsumer.getInstance(),
                                EmptyConsumer.getInstance(), new CollectConsumer<>(commits));
    return commits;
  }

  public static void appendTextFilterParameters(@Nullable String text, boolean regexp, boolean caseSensitive,
                                                @NotNull List<? super String> filterParameters) {
    if (text != null) {
      filterParameters.add(prepareParameter("grep", text));
    }
    filterParameters.add(regexp ? "--extended-regexp" : "--fixed-strings");
    if (!caseSensitive) {
      filterParameters.add("--regexp-ignore-case"); // affects case sensitivity of any filter (except file filter)
    }
  }

  @Nullable
  @Override
  public VcsUser getCurrentUser(@NotNull VirtualFile root) {
    return GitUserRegistry.getInstance(myProject).getOrReadUser(root);
  }

  @NotNull
  @Override
  public Collection<String> getContainingBranches(@NotNull VirtualFile root, @NotNull Hash commitHash) throws VcsException {
    return GitBranchUtil.getBranches(myProject, root, true, true, commitHash.asString());
  }

  @Nullable
  @Override
  @CalledInAny
  public String getCurrentBranch(@NotNull VirtualFile root) {
    GitRepository repository = myRepositoryManager.getRepositoryForRootQuick(root);
    if (repository == null) return null;
    String currentBranchName = repository.getCurrentBranchName();
    if (currentBranchName == null && repository.getCurrentRevision() != null) {
      return GitUtil.HEAD;
    }
    return currentBranchName;
  }

  @Override
  public @NotNull VcsLogDiffHandler getDiffHandler() {
    return new GitLogDiffHandler(myProject);
  }

  @Override
  public @NotNull VcsLogFileHistoryHandler getFileHistoryHandler() {
    return new GitLogHistoryHandler(myProject);
  }

  @Nullable
  @Override
  public Hash resolveReference(@NotNull String ref, @NotNull VirtualFile root) {
    GitRepository repository = myRepositoryManager.getRepositoryForRoot(root);
    if (repository == null) return null;
    return Git.getInstance().resolveReference(repository, ref);
  }

  @Override
  public @NotNull VirtualFile getVcsRoot(@NotNull Project project, @NotNull VirtualFile detectedRoot, @NotNull FilePath path) {
    if (detectedRoot.equals(path.getVirtualFile())) {
      GitRepository repository = myRepositoryManager.getRepositoryForRootQuick(detectedRoot);
      if (repository != null) {
        GitSubmodule submodule = GitSubmoduleKt.asSubmodule(repository);
        if (submodule != null) {
          return submodule.getParent().getRoot();
        }
      }
    }
    return detectedRoot;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public <T> T getPropertyValue(VcsLogProperties.VcsLogProperty<T> property) {
    if (property == VcsLogProperties.LIGHTWEIGHT_BRANCHES) {
      return (T)Boolean.TRUE;
    }
    else if (property == VcsLogProperties.SUPPORTS_INDEXING) {
      return (T)Boolean.valueOf(isIndexingOn());
    }
    else if (property == VcsLogProperties.SUPPORTS_LOG_DIRECTORY_HISTORY) {
      return (T)Boolean.TRUE;
    }
    else if (property == VcsLogProperties.HAS_COMMITTER) {
      return (T)Boolean.TRUE;
    }
    return null;
  }

  @NotNull
  @Override
  public VcsLogIndexer getIndexer() {
    return new GitLogIndexer(myProject, myRepositoryManager);
  }

  public static boolean isIndexingOn() {
    return getIndexingRegistryOption().asBoolean();
  }

  public static @NotNull RegistryValue getIndexingRegistryOption() {
    return Registry.get("vcs.log.index.git");
  }

  private static String prepareParameter(String paramName, String value) {
    return "--" + paramName + "=" + value; // no value quoting needed, because the parameter itself will be quoted by GeneralCommandLine
  }

  private boolean isRepositoryReady(@NotNull VirtualFile root) {
    return isRepositoryReady(myRepositoryManager, root);
  }

  static boolean isRepositoryReady(@NotNull GitRepositoryManager manager, @NotNull VirtualFile root) {
    GitRepository repository = manager.getRepositoryForRoot(root);
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