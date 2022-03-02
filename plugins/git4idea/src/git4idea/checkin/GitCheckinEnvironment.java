// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin;

import com.google.common.collect.HashMultiset;
import com.intellij.diff.util.Side;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ex.PartialCommitHelper;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.impl.PartialChangesUtil;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PairConsumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.vcs.commit.AmendCommitAware;
import com.intellij.vcs.commit.EditedCommitDetails;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcsUtil.VcsFileUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.changes.GitChangeUtils;
import git4idea.changes.GitChangeUtils.GitDiffChange;
import git4idea.checkin.GitCheckinExplicitMovementProvider.Movement;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitConfigUtil;
import git4idea.i18n.GitBundle;
import git4idea.index.GitIndexUtil;
import git4idea.repo.GitCommitTemplateTracker;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.vcs.changes.ChangesUtil.*;
import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.vcs.commit.AbstractCommitWorkflowKt.isAmendCommitMode;
import static com.intellij.vcs.commit.LocalChangesCommitterKt.getCommitWithoutChangesRoots;
import static com.intellij.vcs.commit.ToggleAmendCommitOption.isAmendCommitOptionSupported;
import static git4idea.GitUtil.*;
import static git4idea.checkin.GitCommitAndPushExecutorKt.isPushAfterCommit;
import static git4idea.checkin.GitCommitOptionsKt.*;
import static git4idea.checkin.GitSkipHooksCommitHandlerFactoryKt.isSkipHooks;
import static git4idea.repo.GitSubmoduleKt.isSubmodule;

@Service(Service.Level.PROJECT)
public final class GitCheckinEnvironment implements CheckinEnvironment, AmendCommitAware {
  private static final Logger LOG = Logger.getInstance(GitCheckinEnvironment.class);
  @NonNls private static final String GIT_COMMIT_MSG_FILE_PREFIX = "git-commit-msg-"; // the file name prefix for commit message file
  @NonNls private static final String GIT_COMMIT_MSG_FILE_EXT = ".txt"; // the file extension for commit message file

  private final Project myProject;
  public static final SimpleDateFormat COMMIT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

  private VcsUser myNextCommitAuthor; // The author for the next commit
  private boolean myNextCommitAmend; // If true, the next commit is amended
  private Date myNextCommitAuthorDate;
  private boolean myNextCommitSignOff;
  private boolean myNextCommitSkipHook;
  private boolean myNextCleanupCommitMessage;

  public GitCheckinEnvironment(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }

  @NotNull
  @Override
  public RefreshableOnComponent createCommitOptions(@NotNull CheckinProjectPanel commitPanel, @NotNull CommitContext commitContext) {
    return new GitCheckinOptions(commitPanel, commitContext, isAmendCommitOptionSupported(commitPanel, this));
  }

  @Override
  @Nullable
  public String getDefaultMessageFor(FilePath @NotNull [] filesToCheckin) {
    LinkedHashSet<String> messages = new LinkedHashSet<>();
    GitRepositoryManager manager = getRepositoryManager(myProject);
    Set<GitRepository> repositories = map2SetNotNull(Arrays.asList(filesToCheckin), manager::getRepositoryForFileQuick);
    String commitTemplate = GitCommitTemplateTracker.getInstance(myProject).getTemplateContent();
    if (commitTemplate != null) return commitTemplate;

    for (GitRepository repository : repositories) {
      File mergeMsg = repository.getRepositoryFiles().getMergeMessageFile();
      File squashMsg = repository.getRepositoryFiles().getSquashMessageFile();
      try {
        if (!mergeMsg.exists() && !squashMsg.exists()) {
          continue;
        }
        String encoding = GitConfigUtil.getCommitEncoding(myProject, repository.getRoot());
        if (mergeMsg.exists()) {
          messages.add(loadMessage(mergeMsg, encoding));
        }
        else {
          messages.add(loadMessage(squashMsg, encoding));
        }
      }
      catch (IOException e) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Unable to load merge message", e);
        }
      }
    }
    return DvcsUtil.joinMessagesOrNull(messages);
  }

  private static String loadMessage(@NotNull File messageFile, @NotNull @NonNls String encoding) throws IOException {
    return FileUtil.loadFile(messageFile, encoding);
  }

  @Override
  public String getHelpId() {
    return null;
  }

  @Override
  public String getCheckinOperationName() {
    return GitBundle.message("commit.action.name");
  }

  @Override
  public boolean isAmendCommitSupported() {
    return getAmendService().isAmendCommitSupported();
  }

  @Nullable
  @Override
  public String getLastCommitMessage(@NotNull VirtualFile root) throws VcsException {
    return getAmendService().getLastCommitMessage(root);
  }

  @NotNull
  @Override
  public CancellablePromise<EditedCommitDetails> getAmendCommitDetails(@NotNull VirtualFile root) {
    return getAmendService().getAmendCommitDetails(root);
  }

  @NotNull
  private GitAmendCommitService getAmendService() {
    return myProject.getService(GitAmendCommitService.class);
  }

  private void updateState(@NotNull CommitContext commitContext) {
    myNextCommitAmend = isAmendCommitMode(commitContext);
    myNextCommitSkipHook = isSkipHooks(commitContext);
    myNextCommitAuthor = getCommitAuthor(commitContext);
    myNextCommitAuthorDate = getCommitAuthorDate(commitContext);
    myNextCommitSignOff = isSignOffCommit(commitContext);
    myNextCleanupCommitMessage = GitCommitTemplateTracker.getInstance(myProject).exists();
  }

  @NotNull
  @Override
  public List<VcsException> commit(@NotNull List<? extends Change> changes,
                                   @NotNull @NonNls String commitMessage,
                                   @NotNull CommitContext commitContext,
                                   @NotNull Set<? super String> feedback) {
    updateState(commitContext);

    List<VcsException> exceptions = new ArrayList<>();
    Map<GitRepository, Collection<Change>> sortedChanges = sortChangesByGitRoot(myProject, changes, exceptions);
    Collection<VcsRoot> commitWithoutChangesRoots = getCommitWithoutChangesRoots(commitContext);
    LOG.assertTrue(!sortedChanges.isEmpty() || !commitWithoutChangesRoots.isEmpty(),
                   "Trying to commit an empty list of changes: " + changes);

    List<GitRepository> repositories = collectRepositories(sortedChanges.keySet(), commitWithoutChangesRoots);
    for (GitRepository repository : repositories) {
      Collection<Change> rootChanges = sortedChanges.getOrDefault(repository, emptyList());
      Collection<CommitChange> toCommit = map(rootChanges, CommitChange::new);

      if (isCommitRenamesSeparately(commitContext)) {
        Pair<Collection<CommitChange>, List<VcsException>> pair = commitExplicitRenames(repository, toCommit, commitMessage);
        toCommit = pair.first;
        List<VcsException> moveExceptions = pair.second;

        if (!moveExceptions.isEmpty()) {
          exceptions.addAll(moveExceptions);
          continue;
        }
      }

      exceptions.addAll(commitRepository(repository, toCommit, commitMessage));
    }

    if (isPushAfterCommit(commitContext) && exceptions.isEmpty()) {
      GitPushAfterCommitDialog.showOrPush(myProject, repositories);
    }
    return exceptions;
  }

  @NotNull
  private List<GitRepository> collectRepositories(@NotNull Collection<GitRepository> changesRepositories,
                                                  @NotNull Collection<VcsRoot> noChangesRoots) {
    GitRepositoryManager repositoryManager = getRepositoryManager(myProject);
    GitVcs vcs = GitVcs.getInstance(myProject);
    Collection<GitRepository> noChangesRepositories =
      getRepositoriesFromRoots(repositoryManager, mapNotNull(noChangesRoots, it -> it.getVcs() == vcs ? it.getPath() : null));

    return repositoryManager.sortByDependency(union(changesRepositories, noChangesRepositories));
  }

  @NotNull
  private List<VcsException> commitRepository(@NotNull GitRepository repository,
                                              @NotNull Collection<? extends CommitChange> changes,
                                              @NotNull @NonNls String message) {
    List<VcsException> exceptions = new ArrayList<>();
    VirtualFile root = repository.getRoot();

    try {
      // Stage partial changes
      Pair<List<PartialCommitHelper>, List<CommitChange>> partialAddResult = addPartialChangesToIndex(repository, changes);
      List<PartialCommitHelper> partialCommitHelpers = partialAddResult.first;
      Set<CommitChange> changedWithIndex = new HashSet<>(partialAddResult.second);

      // Stage case-only renames
      List<CommitChange> caseOnlyRenameChanges = addCaseOnlyRenamesToIndex(repository, changes, changedWithIndex, exceptions);
      if (!exceptions.isEmpty()) return exceptions;
      changedWithIndex.addAll(caseOnlyRenameChanges);

      runWithMessageFile(myProject, root, message,
                         messageFile -> exceptions.addAll(commitUsingIndex(myProject, repository, changes, changedWithIndex,
                                                                           messageFile, createCommitOptions())));
      if (!exceptions.isEmpty()) return exceptions;

      applyPartialChanges(partialCommitHelpers);

      getRepositoryManager(myProject).updateRepository(root);
      if (isSubmodule(repository)) {
        VcsDirtyScopeManager.getInstance(myProject).dirDirtyRecursively(repository.getRoot().getParent());
      }
    }
    catch (VcsException e) {
      exceptions.add(e);
    }
    return exceptions;
  }

  @NotNull
  public static List<VcsException> commitUsingIndex(@NotNull Project project,
                                                    @NotNull GitRepository repository,
                                                    @NotNull Collection<? extends ChangedPath> rootChanges,
                                                    @NotNull Set<? extends ChangedPath> changedWithIndex,
                                                    @NotNull File messageFile,
                                                    @NotNull GitCommitOptions commitOptions) {
    List<VcsException> exceptions = new ArrayList<>();
    try {
      Set<FilePath> added = map2SetNotNull(rootChanges, it -> it.afterPath);
      Set<FilePath> removed = map2SetNotNull(rootChanges, it -> it.beforePath);

      VirtualFile root = repository.getRoot();
      String rootPath = root.getPath();

      List<FilePath> unmergedFiles = GitChangeUtils.getUnmergedFiles(repository);
      if (!unmergedFiles.isEmpty()) {
        throw new VcsException(GitBundle.message("error.commit.cant.commit.with.unmerged.paths"));
      }

      // Check what is staged besides our changes
      Collection<GitDiffChange> stagedChanges = GitChangeUtils.getStagedChanges(project, root);
      LOG.debug("Found staged changes: " + getLogStringGitDiffChanges(rootPath, stagedChanges));
      Collection<ChangedPath> excludedStagedChanges = new ArrayList<>();
      Collection<FilePath> excludedStagedAdditions = new ArrayList<>();
      processExcludedPaths(stagedChanges, added, removed, (before, after) -> {
        if (before != null || after != null) excludedStagedChanges.add(new ChangedPath(before, after));
        if (before == null && after != null) excludedStagedAdditions.add(after);
      });

      // Find files with 'AD' status, we will not be able to restore them after using 'git add' command,
      // getting "pathspec 'file.txt' did not match any files" error (and preventing us from adding other files).
      Collection<GitDiffChange> unstagedChanges = GitChangeUtils.getUnstagedChanges(project, root, excludedStagedAdditions, false);
      LOG.debug("Found unstaged changes: " + getLogStringGitDiffChanges(rootPath, unstagedChanges));
      Set<FilePath> excludedUnstagedDeletions = new HashSet<>();
      processExcludedPaths(unstagedChanges, added, removed, (before, after) -> {
        if (before != null && after == null) excludedUnstagedDeletions.add(before);
      });

      if (!excludedStagedChanges.isEmpty()) {
        // Reset staged changes which are not selected for commit
        LOG.info("Staged changes excluded for commit: " + getLogString(rootPath, excludedStagedChanges));
        resetExcluded(project, root, excludedStagedChanges);
      }
      try {
        List<FilePath> alreadyHandledPaths = getPaths(changedWithIndex);
        // Stage what else is needed to commit
        Set<FilePath> toAdd = new HashSet<>(added);
        toAdd.removeAll(alreadyHandledPaths);

        Set<FilePath> toRemove = new HashSet<>(removed);
        toRemove.removeAll(toAdd);
        toRemove.removeAll(alreadyHandledPaths);

        LOG.debug(String.format("Updating index: added: %s, removed: %s", toAdd, toRemove));
        updateIndex(project, root, toAdd, toRemove, exceptions);
        if (!exceptions.isEmpty()) return exceptions;


        // Commit the staging area
        LOG.debug("Performing commit...");
        GitRepositoryCommitter committer = new GitRepositoryCommitter(repository, commitOptions);
        committer.commitStaged(messageFile);
      }
      finally {
        // Stage back the changes unstaged before commit
        if (!excludedStagedChanges.isEmpty()) {
          restoreExcluded(project, root, excludedStagedChanges, excludedUnstagedDeletions);
        }
      }
    }
    catch (VcsException e) {
      exceptions.add(e);
    }
    return exceptions;
  }

  @NotNull
  private GitCommitOptions createCommitOptions() {
    return new GitCommitOptions(myNextCommitAmend, myNextCommitSignOff, myNextCommitSkipHook, myNextCommitAuthor, myNextCommitAuthorDate,
                                myNextCleanupCommitMessage);
  }

  @NotNull
  private Pair<List<PartialCommitHelper>, List<CommitChange>>
  addPartialChangesToIndex(@NotNull GitRepository repository, @NotNull Collection<? extends CommitChange> changes) throws VcsException {
    Set<String> changelistIds = map2SetNotNull(changes, change -> change.changelistId);
    if (changelistIds.isEmpty()) return Pair.create(emptyList(), emptyList());
    if (changelistIds.size() != 1) throw new VcsException(GitBundle.message("error.commit.cant.commit.multiple.changelists"));
    String changelistId = changelistIds.iterator().next();

    Pair<List<PartialCommitHelper>, List<CommitChange>> result = computeAfterLSTManagerUpdate(repository.getProject(), () -> {
      List<PartialCommitHelper> helpers = new ArrayList<>();
      List<CommitChange> partialChanges = new ArrayList<>();

      for (CommitChange change : changes) {
        if (change.changelistId != null && change.virtualFile != null &&
            change.beforePath != null && change.afterPath != null) {
          PartialLocalLineStatusTracker tracker = PartialChangesUtil.getPartialTracker(myProject, change.virtualFile);
          if (tracker != null && tracker.hasPartialChangesToCommit()) {
            if (!tracker.isOperational()) {
              LOG.warn("Tracker is not operational for " + tracker.getVirtualFile().getPresentableUrl());
              return null; // commit failure
            }

            helpers.add(tracker.handlePartialCommit(Side.LEFT, Collections.singletonList(changelistId), true));
            partialChanges.add(change);
          }
        }
      }

      return Pair.create(helpers, partialChanges);
    });

    if (result == null) throw new VcsException(GitBundle.message("error.commit.cant.collect.partial.changes"));
    List<PartialCommitHelper> helpers = result.first;
    List<CommitChange> partialChanges = result.second;


    List<FilePath> pathsToDelete = new ArrayList<>();
    for (CommitChange change : partialChanges) {
      if (change.isMove()) {
        pathsToDelete.add(Objects.requireNonNull(change.beforePath));
      }
    }
    LOG.debug(String.format("Updating index for partial changes: removing: %s", pathsToDelete));
    GitFileUtils.deletePaths(myProject, repository.getRoot(), pathsToDelete, "--ignore-unmatch");


    LOG.debug(String.format("Updating index for partial changes: changes: %s", partialChanges));
    for (int i = 0; i < partialChanges.size(); i++) {
      CommitChange change = partialChanges.get(i);

      FilePath path = Objects.requireNonNull(change.afterPath);
      PartialCommitHelper helper = helpers.get(i);
      VirtualFile file = change.virtualFile;
      if (file == null) throw new VcsException(DiffBundle.message("cannot.find.file.error", path.getPresentableUrl()));

      GitIndexUtil.StagedFile stagedFile = getStagedFile(repository, change);
      boolean isExecutable = stagedFile != null && stagedFile.isExecutable();

      byte[] fileContent = convertDocumentContentToBytesWithBOM(repository, helper.getContent(), file);

      GitIndexUtil.write(repository, path, fileContent, isExecutable);
    }

    return Pair.create(helpers, partialChanges);
  }

  private static void applyPartialChanges(@NotNull List<PartialCommitHelper> partialCommitHelpers) {
    ApplicationManager.getApplication().invokeLater(() -> {
      for (PartialCommitHelper helper : partialCommitHelpers) {
        try {
          helper.applyChanges();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    });
  }

  private static byte @NotNull [] convertDocumentContentToBytes(@NotNull GitRepository repository,
                                                                @NotNull @NonNls String documentContent,
                                                                @NotNull VirtualFile file) {
    String text;

    String lineSeparator = FileDocumentManager.getInstance().getLineSeparator(file, repository.getProject());
    if (lineSeparator.equals("\n")) {
      text = documentContent;
    }
    else {
      text = StringUtil.convertLineSeparators(documentContent, lineSeparator);
    }

    return LoadTextUtil.charsetForWriting(repository.getProject(), file, text, file.getCharset()).second;
  }

  public static byte @NotNull [] convertDocumentContentToBytesWithBOM(@NotNull GitRepository repository,
                                                                      @NotNull @NonNls String documentContent,
                                                                      @NotNull VirtualFile file) {
    byte[] fileContent = convertDocumentContentToBytes(repository, documentContent, file);

    byte[] bom = file.getBOM();
    if (bom != null && !ArrayUtil.startsWith(fileContent, bom)) {
      fileContent = ArrayUtil.mergeArrays(bom, fileContent);
    }

    return fileContent;
  }

  @Nullable
  private static GitIndexUtil.StagedFile getStagedFile(@NotNull GitRepository repository,
                                                       @NotNull CommitChange change) throws VcsException {
    FilePath bPath = change.beforePath;
    if (bPath != null) {
      GitIndexUtil.StagedFile file = GitIndexUtil.listStaged(repository, bPath);
      if (file != null) return file;
    }

    FilePath aPath = change.afterPath;
    if (aPath != null) {
      GitIndexUtil.StagedFile file = GitIndexUtil.listStaged(repository, aPath);
      if (file != null) return file;
    }
    return null;
  }

  @Nullable
  private static <T> T computeAfterLSTManagerUpdate(@NotNull Project project, @NotNull final Computable<T> computation) {
    assert !ApplicationManager.getApplication().isDispatchThread();
    FutureResult<T> ref = new FutureResult<>();
    LineStatusTrackerManager.getInstance(project).invokeAfterUpdate(() -> {
      try {
        ref.set(computation.compute());
      }
      catch (Throwable e) {
        ref.setException(e);
      }
    });
    try {
      return ProgressIndicatorUtils.awaitWithCheckCanceled(ref);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.warn(e);
      return null;
    }
  }


  @NotNull
  private List<CommitChange> addCaseOnlyRenamesToIndex(@NotNull GitRepository repository,
                                                       @NotNull Collection<? extends CommitChange> changes,
                                                       @NotNull Set<CommitChange> alreadyProcessed,
                                                       @NotNull List<? super VcsException> exceptions) {
    if (SystemInfo.isFileSystemCaseSensitive) return Collections.emptyList();

    List<CommitChange> caseOnlyRenames = filter(changes, change -> !alreadyProcessed.contains(change) && isCaseOnlyRename(change));
    if (caseOnlyRenames.isEmpty()) return Collections.emptyList();

    LOG.info("Committing case only rename: " + getLogString(repository.getRoot().getPath(), caseOnlyRenames) +
             " in " + getShortRepositoryName(repository));

    List<FilePath> pathsToAdd = map(caseOnlyRenames, it -> it.afterPath);
    List<FilePath> pathsToDelete = map(caseOnlyRenames, it -> it.beforePath);

    LOG.debug(String.format("Updating index for case only changes: added: %s,\n removed: %s", pathsToAdd, pathsToDelete));
    updateIndex(myProject, repository.getRoot(), pathsToAdd, pathsToDelete, exceptions);

    return caseOnlyRenames;
  }

  private static boolean isCaseOnlyRename(@NotNull ChangedPath change) {
    if (SystemInfo.isFileSystemCaseSensitive) return false;
    if (!change.isMove()) return false;
    FilePath afterPath = Objects.requireNonNull(change.afterPath);
    FilePath beforePath = Objects.requireNonNull(change.beforePath);
    return isCaseOnlyChange(beforePath.getPath(), afterPath.getPath());
  }

  @NotNull
  private static List<FilePath> getPaths(@NotNull Collection<? extends ChangedPath> changes) {
    List<FilePath> files = new ArrayList<>();
    for (ChangedPath change : changes) {
      if (CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(change.beforePath, change.afterPath)) {
        addIfNotNull(files, change.beforePath);
      }
      else {
        addIfNotNull(files, change.beforePath);
        addIfNotNull(files, change.afterPath);
      }
    }
    return files;
  }

  private static void processExcludedPaths(@NotNull Collection<? extends GitDiffChange> changes,
                                           @NotNull Set<FilePath> added,
                                           @NotNull Set<FilePath> removed,
                                           @NotNull PairConsumer<? super FilePath, ? super FilePath> function) {
    for (GitDiffChange change : changes) {
      FilePath before = change.getBeforePath();
      FilePath after = change.getAfterPath();
      if (removed.contains(before)) before = null;
      if (added.contains(after)) after = null;
      function.consume(before, after);
    }
  }

  @NonNls
  @NotNull
  private static String getLogString(@NotNull String root, @NotNull Collection<? extends ChangedPath> changes) {
    return GitUtil.getLogString(root, changes, it -> it.beforePath, it -> it.afterPath);
  }

  @NotNull
  private Pair<Collection<CommitChange>, List<VcsException>> commitExplicitRenames(@NotNull GitRepository repository,
                                                                                   @NotNull Collection<CommitChange> changes,
                                                                                   @NotNull @NonNls String message) {
    List<GitCheckinExplicitMovementProvider> providers =
      filter(GitCheckinExplicitMovementProvider.EP_NAME.getExtensions(), it -> it.isEnabled(myProject));

    List<VcsException> exceptions = new ArrayList<>();
    VirtualFile root = repository.getRoot();

    List<FilePath> beforePaths = mapNotNull(changes, it -> it.beforePath);
    List<FilePath> afterPaths = mapNotNull(changes, it -> it.afterPath);

    Set<Movement> movedPaths = new HashSet<>();
    for (GitCheckinExplicitMovementProvider provider : providers) {
      Collection<Movement> providerMovements = provider.collectExplicitMovements(myProject, beforePaths, afterPaths);
      if (!providerMovements.isEmpty()) {
        message = provider.getCommitMessage(message);
        movedPaths.addAll(providerMovements);
      }
    }

    try {
      Pair<List<CommitChange>, List<CommitChange>> committedAndNewChanges = addExplicitMovementsToIndex(repository, changes, movedPaths);
      if (committedAndNewChanges == null) return Pair.create(changes, exceptions);

      List<CommitChange> movedChanges = committedAndNewChanges.first;
      Collection<CommitChange> newRootChanges = committedAndNewChanges.second;

      runWithMessageFile(myProject, root, message, moveMessageFile -> exceptions.addAll(
        commitUsingIndex(myProject, repository, movedChanges, new HashSet<>(movedChanges), moveMessageFile, createCommitOptions())));

      List<Couple<FilePath>> committedMovements = mapNotNull(movedChanges, it -> Couple.of(it.beforePath, it.afterPath));
      for (GitCheckinExplicitMovementProvider provider : providers) {
        provider.afterMovementsCommitted(myProject, committedMovements);
      }

      return Pair.create(newRootChanges, exceptions);
    }
    catch (VcsException e) {
      exceptions.add(e);
      return Pair.create(changes, exceptions);
    }
  }

  @Nullable
  private Pair<List<CommitChange>, List<CommitChange>> addExplicitMovementsToIndex(@NotNull GitRepository repository,
                                                                                   @NotNull Collection<? extends CommitChange> changes,
                                                                                   @NotNull Collection<? extends Movement> explicitMoves)
    throws VcsException {
    explicitMoves = filterExcludedChanges(explicitMoves, changes);
    if (explicitMoves.isEmpty()) return null;
    LOG.info("Committing explicit rename: " + explicitMoves + " in " + getShortRepositoryName(repository));

    Map<FilePath, Movement> movesMap = new HashMap<>();
    for (Movement move : explicitMoves) {
      movesMap.put(move.getBefore(), move);
      movesMap.put(move.getAfter(), move);
    }


    List<CommitChange> nextCommitChanges = new ArrayList<>();
    List<CommitChange> movedChanges = new ArrayList<>();

    Map<FilePath, CommitChange> affectedBeforePaths = new HashMap<>();
    Map<FilePath, CommitChange> affectedAfterPaths = new HashMap<>();
    for (CommitChange change : changes) {
      if (!movesMap.containsKey(change.beforePath) &&
          !movesMap.containsKey(change.afterPath)) {
        nextCommitChanges.add(change); // is not affected by explicit move
      }
      else {
        if (change.beforePath != null) affectedBeforePaths.put(change.beforePath, change);
        if (change.afterPath != null) affectedAfterPaths.put(change.afterPath, change);
      }
    }


    List<FilePath> pathsToDelete = map(explicitMoves, move -> move.getBefore());
    LOG.debug(String.format("Updating index for explicit movements: removing: %s", pathsToDelete));
    GitFileUtils.deletePaths(myProject, repository.getRoot(), pathsToDelete, "--ignore-unmatch");


    for (Movement move : explicitMoves) {
      FilePath beforeFilePath = move.getBefore();
      FilePath afterFilePath = move.getAfter();
      CommitChange bChange = Objects.requireNonNull(affectedBeforePaths.get(beforeFilePath));
      CommitChange aChange = Objects.requireNonNull(affectedAfterPaths.get(afterFilePath));

      if (bChange.beforeRevision == null) {
        LOG.warn(String.format("Unknown before revision: %s, %s", bChange, aChange));
        continue;
      }

      GitIndexUtil.StagedFile stagedFile = GitIndexUtil.listTree(repository, beforeFilePath, bChange.beforeRevision);
      if (stagedFile == null) {
        LOG.warn(String.format("Can't get revision for explicit move: %s -> %s", beforeFilePath, afterFilePath));
        continue;
      }

      LOG.debug(String.format("Updating index for explicit movements: adding movement: %s -> %s", beforeFilePath, afterFilePath));
      Hash hash = HashImpl.build(stagedFile.getBlobHash());
      boolean isExecutable = stagedFile.isExecutable();
      GitIndexUtil.updateIndex(repository, afterFilePath, hash, isExecutable);

      // We do not use revision numbers after, and it's unclear which numbers should be used. For now, just pass null values.
      nextCommitChanges.add(new CommitChange(afterFilePath, afterFilePath,
                                             null, null,
                                             aChange.changelistId, aChange.virtualFile));
      movedChanges.add(new CommitChange(beforeFilePath, afterFilePath,
                                        null, null,
                                        null, null));

      affectedBeforePaths.remove(beforeFilePath);
      affectedAfterPaths.remove(afterFilePath);
    }

    // Commit leftovers as added/deleted files (ex: if git detected files movements in a conflicting way)
    affectedBeforePaths.forEach((bPath, change) -> nextCommitChanges.add(new CommitChange(change.beforePath, null,
                                                                                        change.beforeRevision, null,
                                                                                        change.changelistId, change.virtualFile)));
    affectedAfterPaths.forEach((aPath, change) -> nextCommitChanges.add(new CommitChange(null, change.afterPath,
                                                                                       null, change.afterRevision,
                                                                                       change.changelistId, change.virtualFile)));

    if (movedChanges.isEmpty()) return null;
    return Pair.create(movedChanges, nextCommitChanges);
  }

  @NotNull
  private static List<Movement> filterExcludedChanges(@NotNull Collection<? extends Movement> explicitMoves,
                                                      @NotNull Collection<? extends CommitChange> changes) {
    HashMultiset<FilePath> movedPathsMultiSet = HashMultiset.create();
    for (Movement move : explicitMoves) {
      movedPathsMultiSet.add(move.getBefore());
      movedPathsMultiSet.add(move.getAfter());
    }

    HashMultiset<FilePath> beforePathsMultiSet = HashMultiset.create();
    HashMultiset<FilePath> afterPathsMultiSet = HashMultiset.create();
    for (CommitChange change : changes) {
      addIfNotNull(beforePathsMultiSet, change.beforePath);
      addIfNotNull(afterPathsMultiSet, change.afterPath);
    }
    return filter(explicitMoves, move -> movedPathsMultiSet.count(move.getBefore()) == 1 && movedPathsMultiSet.count(move.getAfter()) == 1 &&
           beforePathsMultiSet.count(move.getBefore()) == 1 && afterPathsMultiSet.count(move.getAfter()) == 1 &&
           beforePathsMultiSet.count(move.getAfter()) == 0 && afterPathsMultiSet.count(move.getBefore()) == 0);
  }


  private static void resetExcluded(@NotNull Project project,
                                    @NotNull VirtualFile root,
                                    @NotNull Collection<? extends ChangedPath> changes) throws VcsException {
    Set<FilePath> allPaths = CollectionFactory.createCustomHashingStrategySet(CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY);
    for (ChangedPath change : changes) {
      addIfNotNull(allPaths, change.afterPath);
      addIfNotNull(allPaths, change.beforePath);
    }

    for (List<String> paths : VcsFileUtil.chunkPaths(root, allPaths)) {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.RESET);
      handler.endOptions();
      handler.addParameters(paths);
      Git.getInstance().runCommand(handler).throwOnError();
    }
  }

  private static void restoreExcluded(@NotNull Project project,
                                      @NotNull VirtualFile root,
                                      @NotNull Collection<? extends ChangedPath> changes,
                                      @NotNull Set<FilePath> unstagedDeletions) {
    List<VcsException> restoreExceptions = new ArrayList<>();

    Set<FilePath> toAdd = new HashSet<>();
    Set<FilePath> toRemove = new HashSet<>();

    for (ChangedPath change : changes) {
      if (addAsCaseOnlyRename(project, root, change, restoreExceptions)) continue;

      if (change.beforePath == null && unstagedDeletions.contains(change.afterPath)) {
        // we can't restore ADDED-DELETED files
        LOG.info("Ignored added-deleted staged change in " + change.afterPath);
        continue;
      }

      addIfNotNull(toAdd, change.afterPath);
      addIfNotNull(toRemove, change.beforePath);
    }
    toRemove.removeAll(toAdd);

    LOG.debug(String.format("Restoring staged changes after commit: added: %s, removed: %s", toAdd, toRemove));
    updateIndex(project, root, toAdd, toRemove, restoreExceptions);

    for (VcsException e : restoreExceptions) {
      LOG.warn(e);
    }
  }

  private static boolean addAsCaseOnlyRename(@NotNull Project project, @NotNull VirtualFile root, @NotNull ChangedPath change,
                                             @NotNull List<? super VcsException> exceptions) {
    try {
      if (!isCaseOnlyRename(change)) return false;

      FilePath beforePath = Objects.requireNonNull(change.beforePath);
      FilePath afterPath = Objects.requireNonNull(change.afterPath);

      LOG.debug(String.format("Restoring staged case-only rename after commit: %s", change));
      GitLineHandler h = new GitLineHandler(project, root, GitCommand.MV);
      h.addParameters("-f", beforePath.getPath(), afterPath.getPath());
      Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError();
      return true;
    }
    catch (VcsException e) {
      exceptions.add(e);
      return false;
    }
  }

  /**
   * Update index (delete and remove files)
   *
   * @param project    the project
   * @param root       a vcs root
   * @param added      added/modified files to commit
   * @param removed    removed files to commit
   * @param exceptions a list of exceptions to update
   * @return true if index was updated successfully
   */
  private static void updateIndex(final Project project,
                                  final VirtualFile root,
                                  final Collection<? extends FilePath> added,
                                  final Collection<? extends FilePath> removed,
                                  final List<? super VcsException> exceptions) {
    if (!removed.isEmpty()) {
      try {
        GitFileUtils.deletePaths(project, root, removed, "--ignore-unmatch", "--cached", "-r");
      }
      catch (VcsException ex) {
        exceptions.add(ex);
      }
    }
    if (!added.isEmpty()) {
      try {
        GitFileUtils.addPathsForce(project, root, added);
      }
      catch (VcsException ex) {
        exceptions.add(ex);
      }
    }
  }

  /**
   * Create a file that contains the specified message
   *
   * @param root    a git repository root
   * @param message a message to write
   * @return a file reference
   * @throws IOException if file cannot be created
   */
  @NotNull
  public static File createCommitMessageFile(@NotNull Project project, @NotNull VirtualFile root, @NotNull @NonNls String message)
    throws IOException {
    // filter comment lines
    File file = FileUtil.createTempFile(GIT_COMMIT_MSG_FILE_PREFIX, GIT_COMMIT_MSG_FILE_EXT);
    //noinspection SSBasedInspection
    file.deleteOnExit();
    @NonNls String encoding = GitConfigUtil.getCommitEncoding(project, root);
    try (Writer out = new OutputStreamWriter(new FileOutputStream(file), encoding)) {
      out.write(message);
    }
    return file;
  }

  public static void runWithMessageFile(@NotNull Project project, @NotNull VirtualFile root, @NotNull @NonNls String message,
                                        @NotNull ThrowableConsumer<? super File, ? extends VcsException> task) throws VcsException {
    File messageFile;
    try {
      messageFile = createCommitMessageFile(project, root, message);
    }
    catch (IOException ex) {
      throw new VcsException(GitBundle.message("error.commit.cant.create.message.file"), ex);
    }

    try {
      task.consume(messageFile);
    }
    finally {
      if (!messageFile.delete()) {
        LOG.warn("Failed to remove temporary file: " + messageFile);
      }
    }
  }

  @Override
  public List<VcsException> scheduleMissingFileForDeletion(@NotNull List<? extends FilePath> files) {
    ArrayList<VcsException> rc = new ArrayList<>();
    Map<VirtualFile, List<FilePath>> sortedFiles;
    try {
      sortedFiles = sortFilePathsByGitRoot(myProject, files);
    }
    catch (VcsException e) {
      rc.add(e);
      return rc;
    }
    for (Map.Entry<VirtualFile, List<FilePath>> e : sortedFiles.entrySet()) {
      try {
        final VirtualFile root = e.getKey();
        GitFileUtils.deletePaths(myProject, root, e.getValue());
        markRootDirty(root);
      }
      catch (VcsException ex) {
        rc.add(ex);
      }
    }
    return rc;
  }

  @Override
  public List<VcsException> scheduleUnversionedFilesForAddition(@NotNull List<? extends VirtualFile> files) {
    ArrayList<VcsException> rc = new ArrayList<>();
    Map<VirtualFile, List<VirtualFile>> sortedFiles;
    try {
      sortedFiles = sortFilesByGitRoot(myProject, files);
    }
    catch (VcsException e) {
      rc.add(e);
      return rc;
    }
    for (Map.Entry<VirtualFile, List<VirtualFile>> e : sortedFiles.entrySet()) {
      try {
        final VirtualFile root = e.getKey();
        GitFileUtils.addFiles(myProject, root, e.getValue());
        markRootDirty(root);
      }
      catch (VcsException ex) {
        rc.add(ex);
      }
    }
    return rc;
  }

  @NotNull
  private static Map<GitRepository, Collection<Change>> sortChangesByGitRoot(@NotNull Project project,
                                                                             @NotNull List<? extends Change> changes,
                                                                             @NotNull List<? super VcsException> exceptions) {
    Map<GitRepository, Collection<Change>> result = new HashMap<>();
    for (Change change : changes) {
      try {
        // note that any path will work, because changes could happen within single vcs root
        final FilePath filePath = getFilePath(change);

        // the parent paths for calculating roots in order to account for submodules that contribute
        // to the parent change. The path "." is never is valid change, so there should be no problem
        // with it.
        GitRepository repository = getRepositoryForFile(project, Objects.requireNonNull(filePath.getParentPath()));
        Collection<Change> changeList = result.computeIfAbsent(repository, key -> new ArrayList<>());
        changeList.add(change);
      }
      catch (VcsException e) {
        exceptions.add(e);
      }
    }
    return result;
  }

  private void markRootDirty(final VirtualFile root) {
    // Note that the root is invalidated because changes are detected per-root anyway.
    // Otherwise it is not possible to detect moves.
    VcsDirtyScopeManager.getInstance(myProject).dirDirtyRecursively(root);
  }

  @SuppressWarnings("InnerClassMayBeStatic") // used by external plugins
  public class GitCheckinOptions implements CheckinChangeListSpecificComponent, RefreshableOnComponent, Disposable {
    @NotNull private final GitCommitOptionsUi myOptionsUi;

    GitCheckinOptions(@NotNull CheckinProjectPanel commitPanel, @NotNull CommitContext commitContext, boolean showAmendOption) {
      myOptionsUi = new GitCommitOptionsUi(commitPanel, commitContext, showAmendOption);
      Disposer.register(this, myOptionsUi);
    }

    @SuppressWarnings("unused") // used by external plugins
    @Nullable
    public String getAuthor() {
      VcsUser author = myOptionsUi.getAuthor();
      return author != null ? author.toString() : null;
    }

    @SuppressWarnings("unused") // used by external plugins
    public boolean isAmend() {
      return myOptionsUi.getAmendHandler().isAmendCommitMode();
    }

    @Override
    public JComponent getComponent() {
      return myOptionsUi.getComponent();
    }

    @Override
    public void restoreState() {
      myOptionsUi.restoreState();
    }

    @Override
    public void saveState() {
      myOptionsUi.saveState();
    }

    @Override
    public void onChangeListSelected(@NotNull LocalChangeList list) {
      myOptionsUi.onChangeListSelected(list);
    }

    @Override
    public void dispose() {
    }
  }

  @NotNull
  static List<GitCheckinExplicitMovementProvider> collectActiveMovementProviders(@NotNull Project project) {
    GitCheckinExplicitMovementProvider[] allProviders = GitCheckinExplicitMovementProvider.EP_NAME.getExtensions();
    List<GitCheckinExplicitMovementProvider> enabledProviders = filter(allProviders, it -> it.isEnabled(project));
    if (enabledProviders.isEmpty()) return Collections.emptyList();

    List<CommitChange> changes = map(ChangeListManager.getInstance(project).getAllChanges(), CommitChange::new);
    List<FilePath> beforePaths = mapNotNull(changes, it -> it.beforePath);
    List<FilePath> afterPaths = mapNotNull(changes, it -> it.afterPath);

    return filter(enabledProviders, it -> {
      Collection<Movement> movements = it.collectExplicitMovements(project, beforePaths, afterPaths);
      List<Movement> filteredMovements = filterExcludedChanges(movements, changes);
      return !filteredMovements.isEmpty();
    });
  }

  public static class ChangedPath {
    @Nullable public final FilePath beforePath;
    @Nullable public final FilePath afterPath;

    public ChangedPath(@Nullable FilePath beforePath,
                       @Nullable FilePath afterPath) {
      assert beforePath != null || afterPath != null;
      this.beforePath = beforePath;
      this.afterPath = afterPath;
    }

    public boolean isMove() {
      if (beforePath == null || afterPath == null) return false;
      return !CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(beforePath, afterPath);
    }

    @NonNls
    @Override
    public String toString() {
      return String.format("%s -> %s", beforePath, afterPath);
    }
  }

  private static class CommitChange extends ChangedPath {
    @Nullable public final VcsRevisionNumber beforeRevision;
    @Nullable public final VcsRevisionNumber afterRevision;

    @Nullable public final String changelistId;
    @Nullable public final VirtualFile virtualFile;

    CommitChange(@NotNull Change change) {
      super(getBeforePath(change), getAfterPath(change));

      ContentRevision bRev = change.getBeforeRevision();
      ContentRevision aRev = change.getAfterRevision();
      this.beforeRevision = bRev != null ? bRev.getRevisionNumber() : null;
      this.afterRevision = aRev != null ? aRev.getRevisionNumber() : null;

      if (change instanceof ChangeListChange) {
        this.changelistId = ((ChangeListChange)change).getChangeListId();
      }
      else {
        this.changelistId = null;
      }

      if (aRev instanceof CurrentContentRevision) {
        this.virtualFile = ((CurrentContentRevision)aRev).getVirtualFile();
      }
      else {
        this.virtualFile = null;
      }
    }

    CommitChange(@Nullable FilePath beforePath,
                 @Nullable FilePath afterPath,
                 @Nullable VcsRevisionNumber beforeRevision,
                 @Nullable VcsRevisionNumber afterRevision,
                 @Nullable String changelistId,
                 @Nullable VirtualFile virtualFile) {
      super(beforePath, afterPath);
      this.beforeRevision = beforeRevision;
      this.afterRevision = afterRevision;
      this.changelistId = changelistId;
      this.virtualFile = virtualFile;
    }

    @NonNls
    @Override
    public String toString() {
      return super.toString() + ", changelist: " + changelistId;
    }
  }
}
