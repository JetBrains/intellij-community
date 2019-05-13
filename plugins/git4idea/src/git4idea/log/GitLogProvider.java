// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
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
import com.intellij.vcs.log.util.UserNameRegex;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.*;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitBranchesCollection;
import git4idea.config.GitVersionSpecialty;
import git4idea.history.GitLogUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.repo.GitSubmodule;
import git4idea.repo.GitSubmoduleKt;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class GitLogProvider implements VcsLogProvider {

  private static final Logger LOG = Logger.getInstance(GitLogProvider.class);
  public static final Function<VcsRef, String> GET_TAG_NAME = ref -> ref.getType() == GitRefManager.TAG ? ref.getName() : null;
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
    myRefSorter = new GitRefManager(myProject, myRepositoryManager);
    myVcsObjectsFactory = factory;
    myVcs = GitVcs.getInstance(project);
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

    String[] params = new String[]{GitUtil.HEAD, "--branches", "--remotes", "--max-count=" + commitCount};
    // NB: not specifying --tags, because it introduces great slowdown if there are many tags,
    // but makes sense only if there are heads without branch or HEAD labels (rare case). Such cases are partially handled below.

    boolean refresh = requirements instanceof VcsLogProviderRequirementsEx && ((VcsLogProviderRequirementsEx)requirements).isRefresh();

    DetailedLogData data = GitLogUtil.collectMetadata(myProject, root, false, params);

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
    Set<String> tags = newHashSet();
    tags.addAll(GitBranchUtil.getAllTags(myProject, root));
    sw.report();
    return tags;
  }

  @NotNull
  private static <T> Set<T> remove(@NotNull Set<? extends T> original, @NotNull Set<T>... toRemove) {
    Set<T> result = newHashSet(original);
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

    Set<VcsRef> refs = ContainerUtil.newHashSet();
    Set<VcsCommitMetadata> commits = ContainerUtil.newHashSet();
    VcsFileUtil.foreachChunk(new ArrayList<>(unmatchedTags), 1, tagsChunk -> {
      String[] parameters = ArrayUtil.toStringArray(ContainerUtil.concat(params, tagsChunk));
      DetailedLogData logData = GitLogUtil.collectMetadata(myProject, root, false, parameters);
      refs.addAll(logData.getRefs());
      commits.addAll(logData.getCommits());
    });

    sw.report();
    return new LogDataImpl(refs, ContainerUtil.newArrayList(commits));
  }

  @Override
  @NotNull
  public LogData readAllHashes(@NotNull VirtualFile root, @NotNull final Consumer<? super TimedVcsCommit> commitConsumer) throws VcsException {
    if (!isRepositoryReady(root)) {
      return LogDataImpl.empty();
    }

    List<String> parameters = new ArrayList<>(GitLogUtil.LOG_ALL);
    parameters.add("--date-order");

    final GitBekParentFixer parentFixer = GitBekParentFixer.prepare(myProject, root);
    Set<VcsUser> userRegistry = newHashSet();
    Set<VcsRef> refs = newHashSet();
    GitLogUtil.readTimedCommits(myProject, root, parameters, new CollectConsumer<>(userRegistry), new CollectConsumer<>(refs),
                                commit -> commitConsumer.consume(parentFixer.fixCommit(commit)));
    return new LogDataImpl(refs, userRegistry);
  }

  @Override
  public void readAllFullDetails(@NotNull VirtualFile root, @NotNull Consumer<? super VcsFullCommitDetails> commitConsumer) throws VcsException {
    if (!isRepositoryReady(root)) {
      return;
    }

    GitLogUtil.readFullDetails(myProject, root, commitConsumer, shouldIncludeRootChanges(root),
                               false, true, ArrayUtil.toStringArray(GitLogUtil.LOG_ALL));
  }

  @Override
  public void readFullDetails(@NotNull VirtualFile root,
                              @NotNull List<String> hashes,
                              @NotNull Consumer<? super VcsFullCommitDetails> commitConsumer)
    throws VcsException {
    if (!isRepositoryReady(root)) {
      return;
    }

    GitLogUtil.readFullDetailsForHashes(myProject, root, myVcs, commitConsumer, hashes, shouldIncludeRootChanges(root), false,
                                        GitLogUtil.DiffRenameLimit.GIT_CONFIG);
  }

  @Override
  public void readFullDetails(@NotNull VirtualFile root,
                              @NotNull List<String> hashes,
                              @NotNull Consumer<? super VcsFullCommitDetails> commitConsumer,
                              boolean isForIndexing) throws VcsException {
    if (!isRepositoryReady(root)) {
      return;
    }

    GitLogUtil.readFullDetailsForHashes(myProject, root, myVcs, commitConsumer, hashes, shouldIncludeRootChanges(root), isForIndexing,
                                        isForIndexing ? GitLogUtil.DiffRenameLimit.REGISTRY : GitLogUtil.DiffRenameLimit.INFINITY);
  }

  private boolean shouldIncludeRootChanges(@NotNull VirtualFile root) {
    GitRepository repository = getRepository(root);
    if (repository == null) return true;
    return !repository.getInfo().isShallow();
  }

  @NotNull
  @Override
  public List<? extends VcsCommitMetadata> readMetadata(@NotNull final VirtualFile root, @NotNull List<String> hashes)
    throws VcsException {
    return GitLogUtil.collectShortDetails(myProject, myVcs, root, hashes);
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
  public Disposable subscribeToRootRefreshEvents(@NotNull final Collection<? extends VirtualFile> roots, @NotNull final VcsLogRefresher refresher) {
    MessageBusConnection connection = myProject.getMessageBus().connect(myProject);
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
    if (!isRepositoryReady(root)) {
      return Collections.emptyList();
    }

    List<String> filterParameters = ContainerUtil.newArrayList();

    VcsLogBranchFilter branchFilter = filterCollection.get(VcsLogFilterCollection.BRANCH_FILTER);
    VcsLogRevisionFilter revisionFilter = filterCollection.get(VcsLogFilterCollection.REVISION_FILTER);
    if (branchFilter != null || revisionFilter != null) {
      boolean atLeastOneBranchExists = false;

      if (branchFilter != null) {
        GitRepository repository = getRepository(root);
        assert repository != null : "repository is null for root " + root + " but was previously reported as 'ready'";

        Collection<GitBranch> branches = ContainerUtil
          .newArrayList(ContainerUtil.concat(repository.getBranches().getLocalBranches(), repository.getBranches().getRemoteBranches()));
        Collection<String> branchNames = GitBranchUtil.convertBranchesToNames(branches);
        Collection<String> predefinedNames = ContainerUtil.list(GitUtil.HEAD);

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

      if (!atLeastOneBranchExists) { // no such branches in this repository => filter matches nothing
        return Collections.emptyList();
      }
    }
    else {
      filterParameters.addAll(GitLogUtil.LOG_ALL);
    }

    VcsLogDateFilter dateFilter = filterCollection.get(VcsLogFilterCollection.DATE_FILTER);
    if (dateFilter != null) {
      // assuming there is only one date filter, until filter expressions are defined
      if (dateFilter.getAfter() != null) {
        filterParameters.add(prepareParameter("after", dateFilter.getAfter().toString()));
      }
      if (dateFilter.getBefore() != null) {
        filterParameters.add(prepareParameter("before", dateFilter.getBefore().toString()));
      }
    }

    VcsLogTextFilter textFilter = filterCollection.get(VcsLogFilterCollection.TEXT_FILTER);
    String text = textFilter != null ? textFilter.getText() : null;
    boolean regexp = textFilter == null || textFilter.isRegex();
    boolean caseSensitive = textFilter != null && textFilter.matchesCase();
    appendTextFilterParameters(text, regexp, caseSensitive, filterParameters);

    VcsLogUserFilter userFilter = filterCollection.get(VcsLogFilterCollection.USER_FILTER);
    if (userFilter != null) {
      Collection<String> names = ContainerUtil.map(userFilter.getUsers(root), VcsUserUtil::toExactString);
      if (regexp) {
        List<String> authors = ContainerUtil.map(names, UserNameRegex.EXTENDED_INSTANCE);
        if (GitVersionSpecialty.LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR.existsIn(myVcs.getVersion())) {
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
    VcsLogStructureFilter structureFilter = filterCollection.get(VcsLogFilterCollection.STRUCTURE_FILTER);
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

    List<TimedVcsCommit> commits = ContainerUtil.newArrayList();
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
      return GitUtil.HEAD;
    }
    return currentBranchName;
  }

  @Nullable
  @Override
  public VcsLogDiffHandler getDiffHandler() {
    return new GitLogDiffHandler(myProject);
  }

  @Nullable
  @Override
  public VirtualFile getVcsRoot(@NotNull Project project, @NotNull FilePath path) {
    VirtualFile file = path.getVirtualFile();
    if (file != null && file.isDirectory()) {
      GitRepository repository = myRepositoryManager.getRepositoryForRoot(file);
      if (repository != null) {
        GitSubmodule submodule = GitSubmoduleKt.asSubmodule(repository);
        if (submodule != null) {
          return submodule.getParent().getRoot();
        }
      }
    }
    return VcsUtil.getVcsRootFor(project, path);
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
    return null;
  }

  public static boolean isIndexingOn() {
    return Registry.is("vcs.log.index.git");
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
  private static <T> Set<T> newHashSet(@NotNull Collection<? extends T> initialCollection) {
    return new THashSet<>(initialCollection);
  }
}