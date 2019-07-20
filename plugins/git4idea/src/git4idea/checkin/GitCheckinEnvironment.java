// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin;

import com.google.common.collect.HashMultiset;
import com.intellij.CommonBundle;
import com.intellij.diff.util.Side;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.changes.ui.SelectFilePathsDialog;
import com.intellij.openapi.vcs.checkin.CheckinChangeListSpecificComponent;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.ex.PartialCommitHelper;
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager;
import com.intellij.openapi.vcs.impl.PartialChangesUtil;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PairConsumer;
import com.intellij.util.ThrowableConsumer;
import com.intellij.util.concurrency.FutureResult;
import com.intellij.util.textCompletion.DefaultTextCompletionValueDescriptor;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.textCompletion.TextFieldWithCompletion;
import com.intellij.util.textCompletion.ValuesCompletionProvider.ValuesCompletionProviderDumbAware;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.vcs.commit.AmendCommitAware;
import com.intellij.vcs.commit.AmendCommitHandler;
import com.intellij.vcs.commit.AmendCommitModeListener;
import com.intellij.vcs.commit.ToggleAmendCommitOption;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.VcsUserRegistry;
import com.intellij.vcs.log.impl.HashImpl;
import com.intellij.vcs.log.util.VcsUserUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUserRegistry;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.changes.GitChangeUtils;
import git4idea.checkin.GitCheckinExplicitMovementProvider.Movement;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.config.GitVersionSpecialty;
import git4idea.i18n.GitBundle;
import git4idea.index.GitIndexUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitFileUtils;
import gnu.trove.THashSet;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.intellij.dvcs.DvcsUtil.getShortRepositoryName;
import static com.intellij.openapi.util.text.StringUtil.escapeXmlEntities;
import static com.intellij.openapi.vcs.changes.ChangesUtil.*;
import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.ContainerUtil.*;
import static com.intellij.vcs.commit.AbstractCommitWorkflowKt.isAmendCommitMode;
import static com.intellij.vcs.commit.ToggleAmendCommitOption.isAmendCommitOptionSupported;
import static com.intellij.vcs.log.util.VcsUserUtil.isSamePerson;
import static git4idea.GitUtil.*;
import static git4idea.checkin.GitCommitAndPushExecutorKt.isPushAfterCommit;
import static git4idea.repo.GitSubmoduleKt.isSubmodule;
import static java.util.Arrays.asList;
import static one.util.streamex.StreamEx.of;

public class GitCheckinEnvironment implements CheckinEnvironment, AmendCommitAware {
  private static final Logger LOG = Logger.getInstance(GitCheckinEnvironment.class);
  @NonNls private static final String GIT_COMMIT_MSG_FILE_PREFIX = "git-commit-msg-"; // the file name prefix for commit message file
  @NonNls private static final String GIT_COMMIT_MSG_FILE_EXT = ".txt"; // the file extension for commit message file

  private final Project myProject;
  public static final SimpleDateFormat COMMIT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  private final VcsDirtyScopeManager myDirtyScopeManager;
  private final GitVcsSettings mySettings;

  private String myNextCommitAuthor = null; // The author for the next commit
  private boolean myNextCommitAmend; // If true, the next commit is amended
  private Date myNextCommitAuthorDate;
  private boolean myNextCommitSignOff;
  private boolean myNextCommitSkipHook;
  private boolean myNextCommitCommitRenamesSeparately;

  public GitCheckinEnvironment(@NotNull Project project,
                               @NotNull final VcsDirtyScopeManager dirtyScopeManager,
                               final GitVcsSettings settings) {
    myProject = project;
    myDirtyScopeManager = dirtyScopeManager;
    mySettings = settings;
  }

  @Override
  public boolean isRefreshAfterCommitNeeded() {
    return true;
  }

  @NotNull
  @Override
  public RefreshableOnComponent createCommitOptions(@NotNull CheckinProjectPanel commitPanel, @NotNull CommitContext commitContext) {
    return new GitCheckinOptions(myProject, commitPanel, isAmendCommitOptionSupported(commitPanel, this));
  }

  @Override
  @Nullable
  public String getDefaultMessageFor(@NotNull FilePath[] filesToCheckin) {
    LinkedHashSet<String> messages = new LinkedHashSet<>();
    GitRepositoryManager manager = getRepositoryManager(myProject);
    for (VirtualFile root : getRootsForFilePathsIfAny(myProject, asList(filesToCheckin))) {
      GitRepository repository = manager.getRepositoryForRoot(root);
      if (repository == null) { // unregistered nested submodule found by GitUtil.getGitRoot
        LOG.warn("Unregistered repository: " + root);
        continue;
      }
      File mergeMsg = repository.getRepositoryFiles().getMergeMessageFile();
      File squashMsg = repository.getRepositoryFiles().getSquashMessageFile();
      try {
        if (!mergeMsg.exists() && !squashMsg.exists()) {
          continue;
        }
        String encoding = GitConfigUtil.getCommitEncoding(myProject, root);
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

  private static String loadMessage(@NotNull File messageFile, @NotNull String encoding) throws IOException {
    return FileUtil.loadFile(messageFile, encoding);
  }

  @Override
  public String getHelpId() {
    return null;
  }

  @Override
  public String getCheckinOperationName() {
    return GitBundle.getString("commit.action.name");
  }

  @Override
  public boolean isAmendCommitSupported() {
    return true;
  }

  @Nullable
  @Override
  public String getLastCommitMessage(@NotNull VirtualFile root) throws VcsException {
    GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.LOG);
    h.addParameters("--max-count=1");
    h.addParameters("--encoding=UTF-8");
    String formatPattern;
    if (GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(myProject)) {
      formatPattern = "%B";
    }
    else {
      // only message: subject + body; "%-b" means that preceding line-feeds will be deleted if the body is empty
      // %s strips newlines from subject; there is no way to work around it before 1.7.2 with %B (unless parsing some fixed format)
      formatPattern = "%s%n%n%-b";
    }
    h.addParameters("--pretty=format:" + formatPattern);
    return Git.getInstance().runCommand(h).getOutputOrThrow();
  }

  @NotNull
  @Override
  public List<VcsException> commit(@NotNull List<Change> changes,
                                   @NotNull String commitMessage,
                                   @NotNull CommitContext commitContext,
                                   @NotNull Set<String> feedback) {
    myNextCommitAmend = isAmendCommitMode(commitContext);

    GitRepositoryManager manager = getRepositoryManager(myProject);
    List<VcsException> exceptions = new ArrayList<>();
    Map<VirtualFile, Collection<Change>> sortedChanges = sortChangesByGitRoot(myProject, changes, exceptions);
    LOG.assertTrue(!sortedChanges.isEmpty(), "Trying to commit an empty list of changes: " + changes);

    List<GitRepository> repositories = manager.sortByDependency(getRepositoriesFromRoots(manager, sortedChanges.keySet()));
    for (GitRepository repository : repositories) {
      Collection<Change> rootChanges = sortedChanges.get(repository.getRoot());
      Collection<CommitChange> toCommit = map(rootChanges, CommitChange::new);

      if (myNextCommitCommitRenamesSeparately) {
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
      ModalityState modality = ModalityState.defaultModalityState();
      TransactionGuard.getInstance().assertWriteSafeContext(modality);

      List<GitRepository> preselectedRepositories = new ArrayList<>(repositories);
      GuiUtils.invokeLaterIfNeeded(
        () -> new GitPushAfterCommitDialog(myProject, preselectedRepositories,
                                           GitBranchUtil.getCurrentRepository(myProject)).showOrPush(),
        modality, myProject.getDisposed());
    }
    return exceptions;
  }

  @NotNull
  private List<VcsException> commitRepository(@NotNull GitRepository repository,
                                              @NotNull Collection<? extends CommitChange> changes,
                                              @NotNull String message) {
    List<VcsException> exceptions = new ArrayList<>();
    VirtualFile root = repository.getRoot();

    try {
      // Stage partial changes
      Pair<Runnable, List<CommitChange>> partialAddResult = addPartialChangesToIndex(repository, changes);
      Runnable callback = partialAddResult.first;
      Set<CommitChange> changedWithIndex = new HashSet<>(partialAddResult.second);

      // Stage case-only renames
      List<CommitChange> caseOnlyRenameChanges = addCaseOnlyRenamesToIndex(repository, changes, changedWithIndex, exceptions);
      if (!exceptions.isEmpty()) return exceptions;
      changedWithIndex.addAll(caseOnlyRenameChanges);

      if (!changedWithIndex.isEmpty() || Registry.is("git.force.commit.using.staging.area")) {
        runWithMessageFile(myProject, root, message, messageFile -> exceptions.addAll(commitUsingIndex(repository, changes, changedWithIndex, messageFile)));
        if (!exceptions.isEmpty()) return exceptions;

        callback.run();
      }
      else {
        try {
          runWithMessageFile(myProject, root, message, messageFile -> {
            List<FilePath> files = getPaths(changes);
            commit(myProject, root, files, messageFile);
          });
        }
        catch (VcsException ex) {
          PartialOperation partialOperation = isMergeCommit(ex);
          if (partialOperation == PartialOperation.NONE) {
            throw ex;
          }
          runWithMessageFile(myProject, root, message, messageFile -> {
            if (!mergeCommit(myProject, root, changes, messageFile, exceptions, partialOperation)) {
              throw ex;
            }
          });
        }
      }

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
  private List<VcsException> commitUsingIndex(@NotNull GitRepository repository,
                                              @NotNull Collection<? extends CommitChange> rootChanges,
                                              @NotNull Set<? extends CommitChange> changedWithIndex,
                                              @NotNull File messageFile) {
    List<VcsException> exceptions = new ArrayList<>();
    try {
      Set<FilePath> added = map2SetNotNull(rootChanges, it -> it.afterPath);
      Set<FilePath> removed = map2SetNotNull(rootChanges, it -> it.beforePath);

      VirtualFile root = repository.getRoot();
      String rootPath = root.getPath();

      List<File> unmergedFiles = GitChangeUtils.getUnmergedFiles(repository);
      if (!unmergedFiles.isEmpty()) {
        throw new VcsException("Committing is not possible because you have unmerged files.");
      }

      // Check what is staged besides our changes
      Collection<Change> stagedChanges = GitChangeUtils.getStagedChanges(myProject, root);
      LOG.debug("Found staged changes: " + GitUtil.getLogString(rootPath, stagedChanges));
      Collection<ChangedPath> excludedStagedChanges = new ArrayList<>();
      processExcludedPaths(stagedChanges, added, removed, (before, after) -> {
        if (before != null || after != null) excludedStagedChanges.add(new ChangedPath(before, after));
      });

      // Find unstaged deletions, we might not be able to restore them after
      Collection<Change> unstagedChanges = GitChangeUtils.getUnstagedChanges(myProject, root, false);
      LOG.debug("Found unstaged changes: " + GitUtil.getLogString(rootPath, unstagedChanges));
      Set<FilePath> excludedUnstagedDeletions = new HashSet<>();
      processExcludedPaths(unstagedChanges, added, removed, (before, after) -> {
        if (before != null && after == null) excludedUnstagedDeletions.add(before);
      });

      if (!excludedStagedChanges.isEmpty()) {
        // Reset staged changes which are not selected for commit
        LOG.info("Staged changes excluded for commit: " + getLogString(rootPath, excludedStagedChanges));
        resetExcluded(myProject, root, excludedStagedChanges);
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
        updateIndex(myProject, root, toAdd, toRemove, exceptions);
        if (!exceptions.isEmpty()) return exceptions;


        // Commit the staging area
        LOG.debug("Performing commit...");
        commitWithoutPaths(myProject, root, messageFile);
      }
      finally {
        // Stage back the changes unstaged before commit
        if (!excludedStagedChanges.isEmpty()) {
          restoreExcluded(myProject, root, excludedStagedChanges, excludedUnstagedDeletions);
        }
      }
    }
    catch (VcsException e) {
      exceptions.add(e);
    }
    return exceptions;
  }


  @NotNull
  private Pair<Runnable, List<CommitChange>> addPartialChangesToIndex(@NotNull GitRepository repository,
                                                                      @NotNull Collection<? extends CommitChange> changes) throws VcsException {
    Set<String> changelistIds = map2SetNotNull(changes, change -> change.changelistId);
    if (changelistIds.isEmpty()) return Pair.create(EmptyRunnable.INSTANCE, emptyList());
    if (changelistIds.size() != 1) throw new VcsException("Can't commit changes from multiple changelists at once");
    String changelistId = changelistIds.iterator().next();

    Pair<List<PartialCommitHelper>, List<CommitChange>> result = computeAfterLSTManagerUpdate(repository.getProject(), () -> {
      List<PartialCommitHelper> helpers = new ArrayList<>();
      List<CommitChange> partialChanges = new ArrayList<>();

      for (CommitChange change : changes) {
        if (change.changelistId != null && change.virtualFile != null &&
            change.beforePath != null && change.afterPath != null) {
          PartialLocalLineStatusTracker tracker = PartialChangesUtil.getPartialTracker(myProject, change.virtualFile);

          if (tracker == null) continue;
          if (!tracker.isOperational()) {
            LOG.warn("Tracker is not operational for " + tracker.getVirtualFile().getPresentableUrl());
            return null; // commit failure
          }

          if (tracker.hasPartialChangesToCommit()) {
            helpers.add(tracker.handlePartialCommit(Side.LEFT, Collections.singletonList(changelistId), true));
            partialChanges.add(change);
          }
        }
      }

      return Pair.create(helpers, partialChanges);
    });

    if (result == null) throw new VcsException("Can't collect partial changes to commit");
    List<PartialCommitHelper> helpers = result.first;
    List<CommitChange> partialChanges = result.second;


    List<FilePath> pathsToDelete = new ArrayList<>();
    for (CommitChange change : partialChanges) {
      if (change.isMove()) {
        pathsToDelete.add(assertNotNull(change.beforePath));
      }
    }
    LOG.debug(String.format("Updating index for partial changes: removing: %s", pathsToDelete));
    GitFileUtils.deletePaths(myProject, repository.getRoot(), pathsToDelete, "--ignore-unmatch");


    LOG.debug(String.format("Updating index for partial changes: changes: %s", partialChanges));
    for (int i = 0; i < partialChanges.size(); i++) {
      CommitChange change = partialChanges.get(i);

      FilePath path = assertNotNull(change.afterPath);
      PartialCommitHelper helper = helpers.get(i);
      VirtualFile file = change.virtualFile;
      if (file == null) throw new VcsException("Can't find file: " + path.getPath());

      GitIndexUtil.StagedFile stagedFile = getStagedFile(repository, change);
      boolean isExecutable = stagedFile != null && stagedFile.isExecutable();

      byte[] fileContent = convertDocumentContentToBytes(repository, helper.getContent(), file);

      GitIndexUtil.write(repository, path, fileContent, isExecutable);
    }


    Runnable callback = () -> ApplicationManager.getApplication().invokeLater(() -> {
      for (PartialCommitHelper helper : helpers) {
        try {
          helper.applyChanges();
        }
        catch (Throwable e) {
          LOG.error(e);
        }
      }
    });

    return Pair.create(callback, partialChanges);
  }

  @NotNull
  private static byte[] convertDocumentContentToBytes(@NotNull GitRepository repository,
                                                      @NotNull String documentContent,
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
      return ref.get();
    }
    catch (InterruptedException | ExecutionException e) {
      return null;
    }
  }


  @NotNull
  private List<CommitChange> addCaseOnlyRenamesToIndex(@NotNull GitRepository repository,
                                                       @NotNull Collection<? extends CommitChange> changes,
                                                       @NotNull Set<CommitChange> alreadyProcessed,
                                                       @NotNull List<? super VcsException> exceptions) {
    if (SystemInfo.isFileSystemCaseSensitive) return Collections.emptyList();

    List<CommitChange> caseOnlyRenames = filter(changes, it -> !alreadyProcessed.contains(it) && isCaseOnlyRename(it));
    if (caseOnlyRenames.isEmpty()) return caseOnlyRenames;

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
    FilePath afterPath = assertNotNull(change.afterPath);
    FilePath beforePath = assertNotNull(change.beforePath);
    return isCaseOnlyChange(beforePath.getPath(), afterPath.getPath());
  }

  @NotNull
  private static List<FilePath> getPaths(@NotNull Collection<? extends CommitChange> changes) {
    List<FilePath> files = new ArrayList<>();
    for (CommitChange change : changes) {
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

  private static void processExcludedPaths(@NotNull Collection<? extends Change> changes,
                                           @NotNull Set<FilePath> added,
                                           @NotNull Set<FilePath> removed,
                                           @NotNull PairConsumer<? super FilePath, ? super FilePath> function) {
    for (Change change : changes) {
      FilePath before = getBeforePath(change);
      FilePath after = getAfterPath(change);
      if (removed.contains(before)) before = null;
      if (added.contains(after)) after = null;
      function.consume(before, after);
    }
  }

  @NotNull
  private static String getLogString(@NotNull String root, @NotNull Collection<? extends ChangedPath> changes) {
    return GitUtil.getLogString(root, changes, it -> it.beforePath, it -> it.afterPath);
  }

  @NotNull
  private Pair<Collection<CommitChange>, List<VcsException>> commitExplicitRenames(@NotNull GitRepository repository,
                                                                                   @NotNull Collection<CommitChange> changes,
                                                                                   @NotNull String message) {
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

      runWithMessageFile(myProject, root, message, moveMessageFile -> exceptions.addAll(commitUsingIndex(repository, movedChanges, new HashSet<>(movedChanges), moveMessageFile)));

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
      CommitChange bChange = assertNotNull(affectedBeforePaths.get(beforeFilePath));
      CommitChange aChange = assertNotNull(affectedAfterPaths.get(afterFilePath));

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
    Set<FilePath> allPaths = new THashSet<>(CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY);
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

      FilePath beforePath = assertNotNull(change.beforePath);
      FilePath afterPath = assertNotNull(change.afterPath);

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

  private boolean mergeCommit(@NotNull Project project,
                              @NotNull VirtualFile root,
                              @NotNull Collection<? extends CommitChange> rootChanges,
                              @NotNull File messageFile,
                              @NotNull List<? super VcsException> exceptions,
                              @NotNull PartialOperation partialOperation) {
    Set<FilePath> added = map2SetNotNull(rootChanges, it -> it.afterPath);
    Set<FilePath> removed = map2SetNotNull(rootChanges, it -> it.beforePath);
    removed.removeAll(added);

    HashSet<FilePath> realAdded = new HashSet<>();
    HashSet<FilePath> realRemoved = new HashSet<>();
    // perform diff
    GitLineHandler diff = new GitLineHandler(project, root, GitCommand.DIFF);
    diff.setSilent(true);
    diff.setStdoutSuppressed(true);
    diff.addParameters("--diff-filter=ADMRUX", "--name-status", "--no-renames", "HEAD");
    diff.endOptions();
    String output;
    try {
      output = Git.getInstance().runCommand(diff).getOutputOrThrow();
    }
    catch (VcsException ex) {
      exceptions.add(ex);
      return false;
    }
    String rootPath = root.getPath();
    for (StringTokenizer lines = new StringTokenizer(output, "\n", false); lines.hasMoreTokens(); ) {
      String line = lines.nextToken().trim();
      if (line.length() == 0) {
        continue;
      }
      String[] tk = line.split("\t");
      switch (tk[0].charAt(0)) {
        case 'M':
        case 'A':
          realAdded.add(VcsUtil.getFilePath(rootPath + "/" + tk[1]));
          break;
        case 'D':
          realRemoved.add(VcsUtil.getFilePath(rootPath + "/" + tk[1], false));
          break;
        default:
          throw new IllegalStateException("Unexpected status: " + line);
      }
    }
    realAdded.removeAll(added);
    realRemoved.removeAll(removed);
    if (realAdded.size() != 0 || realRemoved.size() != 0) {

      final List<FilePath> files = new ArrayList<>();
      files.addAll(realAdded);
      files.addAll(realRemoved);
      Ref<Boolean> mergeAll = new Ref<>();
      try {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          String message = GitBundle.message("commit.partial.merge.message", partialOperation.getName());
          SelectFilePathsDialog dialog = new SelectFilePathsDialog(project, files, message, null, "Commit All Files",
                                                                   CommonBundle.getCancelButtonText(), false);
          dialog.setTitle(GitBundle.getString("commit.partial.merge.title"));
          dialog.show();
          mergeAll.set(dialog.isOK());
        });
      }
      catch (RuntimeException ex) {
        throw ex;
      }
      catch (Exception ex) {
        throw new RuntimeException("Unable to invoke a message box on AWT thread", ex);
      }
      if (!mergeAll.get()) {
        return false;
      }
      // update non-indexed files
      if (!updateIndex(project, root, realAdded, realRemoved, exceptions)) {
        return false;
      }
      for (FilePath f : realAdded) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(f);
      }
      for (FilePath f : realRemoved) {
        VcsDirtyScopeManager.getInstance(project).fileDirty(f);
      }
    }
    // perform merge commit
    try {
      commitWithoutPaths(project, root, messageFile);
    }
    catch (VcsException ex) {
      exceptions.add(ex);
      return false;
    }
    return true;
  }

  private void commitWithoutPaths(@NotNull Project project,
                                  @NotNull VirtualFile root,
                                  @NotNull File messageFile) throws VcsException {
    GitLineHandler handler = new GitLineHandler(project, root, GitCommand.COMMIT);
    handler.setStdoutSuppressed(false);
    handler.addParameters("-F");
    handler.addAbsoluteFile(messageFile);
    if (myNextCommitAmend) {
      handler.addParameters("--amend");
    }
    if (myNextCommitAuthor != null) {
      handler.addParameters("--author=" + myNextCommitAuthor);
    }
    if (myNextCommitAuthorDate != null) {
      handler.addParameters("--date", COMMIT_DATE_FORMAT.format(myNextCommitAuthorDate));
    }
    if (myNextCommitSignOff) {
      handler.addParameters("--signoff");
    }
    if (myNextCommitSkipHook) {
      handler.addParameters("--no-verify");
    }
    handler.endOptions();
    Git.getInstance().runCommand(handler).throwOnError();
  }

  /**
   * Check if commit has failed due to unfinished merge or cherry-pick.
   *
   * @param ex an exception to examine
   * @return true if exception means that there is a partial commit during merge
   */
  private static PartialOperation isMergeCommit(final VcsException ex) {
    String message = ex.getMessage();
    if (message.contains("cannot do a partial commit during a merge")) {
      return PartialOperation.MERGE;
    }
    if (message.contains("cannot do a partial commit during a cherry-pick")) {
      return PartialOperation.CHERRY_PICK;
    }
    return PartialOperation.NONE;
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
  private static boolean updateIndex(final Project project,
                                     final VirtualFile root,
                                     final Collection<? extends FilePath> added,
                                     final Collection<? extends FilePath> removed,
                                     final List<? super VcsException> exceptions) {
    boolean rc = true;
    if (!removed.isEmpty()) {
      try {
        GitFileUtils.deletePaths(project, root, removed, "--ignore-unmatch", "--cached", "-r");
      }
      catch (VcsException ex) {
        exceptions.add(ex);
        rc = false;
      }
    }
    if (!added.isEmpty()) {
      try {
        GitFileUtils.addPathsForce(project, root, added);
      }
      catch (VcsException ex) {
        exceptions.add(ex);
        rc = false;
      }
    }
    return rc;
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
  public static File createCommitMessageFile(@NotNull Project project, @NotNull VirtualFile root, @NotNull String message)
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

  private static void runWithMessageFile(@NotNull Project project, @NotNull VirtualFile root, @NotNull String message,
                                         @NotNull ThrowableConsumer<? super File, ? extends VcsException> task) throws VcsException {
    File messageFile;
    try {
      messageFile = createCommitMessageFile(project, root, message);
    }
    catch (IOException ex) {
      throw new VcsException("Creation of commit message file failed", ex);
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
  public List<VcsException> scheduleMissingFileForDeletion(@NotNull List<FilePath> files) {
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

  private void commit(@NotNull Project project, @NotNull VirtualFile root, @NotNull Collection<? extends FilePath> files, @NotNull File messageFile)
    throws VcsException {
    boolean amend = myNextCommitAmend;
    for (List<String> paths : VcsFileUtil.chunkPaths(root, files)) {
      GitLineHandler handler = new GitLineHandler(project, root, GitCommand.COMMIT);
      handler.setStdoutSuppressed(false);
      if (myNextCommitSignOff) {
        handler.addParameters("--signoff");
      }
      if (amend) {
        handler.addParameters("--amend");
      }
      else {
        amend = true;
      }
      if (myNextCommitSkipHook) {
        handler.addParameters("--no-verify");
      }
      handler.addParameters("--only");
      handler.addParameters("-F");
      handler.addAbsoluteFile(messageFile);
      if (myNextCommitAuthor != null) {
        handler.addParameters("--author=" + myNextCommitAuthor);
      }
      if (myNextCommitAuthorDate != null) {
        handler.addParameters("--date", COMMIT_DATE_FORMAT.format(myNextCommitAuthorDate));
      }
      handler.endOptions();
      handler.addParameters(paths);
      Git.getInstance().runCommand(handler).throwOnError();
    }
  }

  @Override
  public List<VcsException> scheduleUnversionedFilesForAddition(@NotNull List<VirtualFile> files) {
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

  private enum PartialOperation {
    NONE("none"),
    MERGE("merge"),
    CHERRY_PICK("cherry-pick");

    private final String myName;

    PartialOperation(String name) {
      myName = name;
    }

    String getName() {
      return myName;
    }
  }

  @NotNull
  private static Map<VirtualFile, Collection<Change>> sortChangesByGitRoot(@NotNull Project project,
                                                                           @NotNull List<? extends Change> changes,
                                                                           @NotNull List<? super VcsException> exceptions) {
    Map<VirtualFile, Collection<Change>> result = new HashMap<>();
    for (Change change : changes) {
      try {
        // note that any path will work, because changes could happen within single vcs root
        final FilePath filePath = getFilePath(change);

        // the parent paths for calculating roots in order to account for submodules that contribute
        // to the parent change. The path "." is never is valid change, so there should be no problem
        // with it.
        GitRepository repository = getRepositoryForFile(project, assertNotNull(filePath.getParentPath()));
        Collection<Change> changeList = result.computeIfAbsent(repository.getRoot(), key -> new ArrayList<>());
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
    myDirtyScopeManager.dirDirtyRecursively(root);
  }

  public void reset() {
    myNextCommitAuthor = null;
    myNextCommitAuthorDate = null;
    myNextCommitSkipHook = false;
  }

  public class GitCheckinOptions
    implements CheckinChangeListSpecificComponent, RefreshableOnComponent, AmendCommitModeListener, Disposable {
    private final List<GitCheckinExplicitMovementProvider> myExplicitMovementProviders;

    @NotNull private final CheckinProjectPanel myCheckinProjectPanel;
    @NotNull private final JPanel myPanel;
    @NotNull private final EditorTextField myAuthorField;
    @Nullable private Date myAuthorDate;
    @NotNull private final JCheckBox mySignOffCheckbox;
    @NotNull private final JCheckBox myCommitRenamesSeparatelyCheckbox;
    @NotNull private final BalloonBuilder myAuthorNotificationBuilder;
    @Nullable private Balloon myAuthorBalloon;

    GitCheckinOptions(@NotNull Project project, @NotNull CheckinProjectPanel panel, boolean showAmendOption) {
      myExplicitMovementProviders = collectActiveMovementProviders(myProject);

      myCheckinProjectPanel = panel;
      myAuthorField = createTextField(project, getAuthors(project));
      myAuthorField.addFocusListener(new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
          clearAuthorWarn();
        }
      });
      myAuthorNotificationBuilder = JBPopupFactory.getInstance().
        createBalloonBuilder(new JLabel(GitBundle.getString("commit.author.diffs"))).
        setBorderInsets(UIManager.getInsets("Balloon.error.textInsets")).
        setBorderColor(JBUI.CurrentTheme.Validator.warningBorderColor()).
        setFillColor(JBUI.CurrentTheme.Validator.warningBackgroundColor()).
        setHideOnClickOutside(true).
        setHideOnFrameResize(false);
      myAuthorField.addHierarchyListener(new HierarchyListener() {
        @Override
        public void hierarchyChanged(HierarchyEvent e) {
          if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && myAuthorField.isShowing()) {
            if (!StringUtil.isEmptyOrSpaces(myAuthorField.getText())) {
              showAuthorBalloonNotification();
              myAuthorField.removeHierarchyListener(this);
            }
          }
        }
      });
      JLabel authorLabel = new JBLabel(GitBundle.message("commit.author"));
      authorLabel.setLabelFor(myAuthorField);

      ToggleAmendCommitOption amendOption = showAmendOption ? new ToggleAmendCommitOption(myCheckinProjectPanel, this) : null;

      mySignOffCheckbox = new JBCheckBox("Sign-off commit", mySettings.shouldSignOffCommit());
      mySignOffCheckbox.setMnemonic(KeyEvent.VK_G);
      mySignOffCheckbox.setToolTipText(getToolTip(project, panel));
      myCommitRenamesSeparatelyCheckbox = new JBCheckBox(getExplicitMovementDescription(), mySettings.isCommitRenamesSeparately());

      GridBag gb = new GridBag().
        setDefaultAnchor(GridBagConstraints.WEST).
        setDefaultInsets(JBUI.insets(2));
      myPanel = new JPanel(new GridBagLayout());
      myPanel.add(authorLabel, gb.nextLine().next());
      myPanel.add(myAuthorField, gb.next().fillCellHorizontally().weightx(1));
      if (amendOption != null) myPanel.add(amendOption, gb.nextLine().next().coverLine());
      myPanel.add(mySignOffCheckbox, gb.nextLine().next().coverLine());
      myPanel.add(myCommitRenamesSeparatelyCheckbox, gb.nextLine().next().coverLine());

      getAmendHandler().addAmendCommitModeListener(this, this);
    }

    @NotNull
    private AmendCommitHandler getAmendHandler() {
      return myCheckinProjectPanel.getCommitWorkflowHandler().getAmendCommitHandler();
    }

    @Override
    public void dispose() {
    }

    @Override
    public void amendCommitModeToggled() {
      updateRenamesCheckboxState();
    }

    public boolean isAmend() {
      return getAmendHandler().isAmendCommitMode();
    }

    @Nullable
    public String getAuthor() {
      String author = myAuthorField.getText();
      if (StringUtil.isEmptyOrSpaces(author)) return null;
      return GitCommitAuthorCorrector.correct(author);
    }

    @NotNull
    private String getToolTip(@NotNull Project project, @NotNull CheckinProjectPanel panel) {
      VcsUser user = getFirstItem(mapNotNull(panel.getRoots(), it -> GitUserRegistry.getInstance(project).getUser(it)));
      String signature = user != null ? escapeXmlEntities(VcsUserUtil.toExactString(user)) : "";
      return "<html>Adds the following line at the end of the commit message:<br/>" +
             "Signed-off by: " + signature + "</html>";
    }

    @NotNull
    private String getExplicitMovementDescription() {
      if (myExplicitMovementProviders.size() == 1) {
        return myExplicitMovementProviders.get(0).getDescription();
      }
      return "Create extra commit with file movements";
    }

    @CalledInAwt
    private void showAuthorBalloonNotification() {
      if (myAuthorBalloon == null || myAuthorBalloon.isDisposed()) {
        myAuthorBalloon = myAuthorNotificationBuilder.createBalloon();
        myAuthorBalloon.show(new RelativePoint(myAuthorField, new Point(myAuthorField.getWidth() / 2, myAuthorField.getHeight())),
                             Balloon.Position.below);
      }
    }

    @NotNull
    private List<String> getAuthors(@NotNull Project project) {
      Set<String> authors = new HashSet<>(getUsersList(project));
      addAll(authors, mySettings.getCommitAuthors());
      List<String> list = new ArrayList<>(authors);
      Collections.sort(list);
      return list;
    }

    @NotNull
    private EditorTextField createTextField(@NotNull Project project, @NotNull List<String> list) {
      TextCompletionProvider completionProvider =
        new ValuesCompletionProviderDumbAware<>(new DefaultTextCompletionValueDescriptor.StringValueDescriptor(), list);
      return new TextFieldWithCompletion(project, completionProvider, "", true, true, true);
    }

    @NotNull
    private List<String> getUsersList(@NotNull Project project) {
      VcsUserRegistry userRegistry = ServiceManager.getService(project, VcsUserRegistry.class);
      return map(userRegistry.getUsers(), VcsUserUtil::toExactString);
    }

    private void updateRenamesCheckboxState() {
      if (myExplicitMovementProviders.isEmpty() || !Registry.is("git.allow.explicit.commit.renames")) {
        myCommitRenamesSeparatelyCheckbox.setVisible(false);
        myCommitRenamesSeparatelyCheckbox.setEnabled(false);
      }
      else {
        myCommitRenamesSeparatelyCheckbox.setVisible(true);
        myCommitRenamesSeparatelyCheckbox.setEnabled(!isAmend());
      }
    }

    @Override
    public void refresh() {
      updateRenamesCheckboxState();
      myAuthorField.setText(null);
      clearAuthorWarn();
      myAuthorDate = null;
      reset();
    }

    @Override
    public void saveState() {
      myNextCommitAuthor = getAuthor();
      if (myNextCommitAuthor != null) {
        mySettings.saveCommitAuthor(myNextCommitAuthor);
      }
      myNextCommitAuthorDate = myAuthorDate;

      mySettings.setSignOffCommit(mySignOffCheckbox.isSelected());
      myNextCommitSignOff = mySignOffCheckbox.isSelected();

      mySettings.setCommitRenamesSeparately(myCommitRenamesSeparatelyCheckbox.isSelected());
      myNextCommitCommitRenamesSeparately = myCommitRenamesSeparatelyCheckbox.isEnabled() && myCommitRenamesSeparatelyCheckbox.isSelected();
    }

    @Override
    public void restoreState() {
      refresh();
    }

    @Override
    public void onChangeListSelected(LocalChangeList list) {
      updateRenamesCheckboxState();
      Object data = list.getData();
      clearAuthorWarn();
      if (data instanceof ChangeListData) {
        fillAuthorAndDateFromData((ChangeListData)data);
      }
      else {
        myAuthorField.setText(null);
        myAuthorDate = null;
      }
      myPanel.revalidate();
      myPanel.repaint();
    }

    private void fillAuthorAndDateFromData(@NotNull ChangeListData data) {
      VcsUser author = data.getAuthor();
      if (author != null && !isDefaultAuthor(author)) {
        myAuthorField.setText(VcsUserUtil.toExactString(author));
        myAuthorField.putClientProperty("JComponent.outline", "warning");
        if (myAuthorField.isShowing()) {
          showAuthorBalloonNotification();
        }
      }
      else {
        myAuthorField.setText(null);
      }
      myAuthorDate = data.getDate();
    }

    private void clearAuthorWarn() {
      myAuthorField.putClientProperty("JComponent.outline", null);
      if (myAuthorBalloon != null) {
        myAuthorBalloon.hide();
        myAuthorBalloon = null;
      }
    }

    @Override
    public JComponent getComponent() {
      return myPanel;
    }

    public boolean isDefaultAuthor(@NotNull VcsUser author) {
      GitRepositoryManager manager = getRepositoryManager(myProject);
      Collection<VirtualFile> affectedGitRoots = filter(myCheckinProjectPanel.getRoots(),
                                                        root -> manager.getRepositoryForRoot(root) != null);
      GitUserRegistry gitUserRegistry = GitUserRegistry.getInstance(myProject);
      return of(affectedGitRoots).map(vf -> gitUserRegistry.getUser(vf)).allMatch(user -> user != null && isSamePerson(author, user));
    }

    @NotNull
    private List<GitCheckinExplicitMovementProvider> collectActiveMovementProviders(@NotNull Project project) {
      GitCheckinExplicitMovementProvider[] allProviders = GitCheckinExplicitMovementProvider.EP_NAME.getExtensions();
      List<GitCheckinExplicitMovementProvider> enabledProviders = filter(allProviders, it -> it.isEnabled(project));
      if (enabledProviders.isEmpty()) return Collections.emptyList();
      if (Registry.is("git.explicit.commit.renames.prohibit.multiple.calls")) return enabledProviders;

      List<CommitChange> changes = map(ChangeListManager.getInstance(project).getAllChanges(), CommitChange::new);
      List<FilePath> beforePaths = mapNotNull(changes, it -> it.beforePath);
      List<FilePath> afterPaths = mapNotNull(changes, it -> it.afterPath);

      return filter(enabledProviders, it -> {
        Collection<Movement> movements = it.collectExplicitMovements(project, beforePaths, afterPaths);
        List<Movement> filteredMovements = filterExcludedChanges(movements, changes);
        return !filteredMovements.isEmpty();
      });
    }
  }

  public void setSkipHooksForNextCommit(boolean skipHooksForNextCommit) {
    myNextCommitSkipHook = skipHooksForNextCommit;
  }

  @TestOnly
  public void setCommitRenamesSeparately(boolean commitRenamesSeparately) {
    myNextCommitCommitRenamesSeparately = commitRenamesSeparately;
  }

  private static class ChangedPath {
    @Nullable public final FilePath beforePath;
    @Nullable public final FilePath afterPath;

    ChangedPath(@Nullable FilePath beforePath,
                @Nullable FilePath afterPath) {
      assert beforePath != null || afterPath != null;
      this.beforePath = beforePath;
      this.afterPath = afterPath;
    }

    public boolean isMove() {
      if (beforePath == null || afterPath == null) return false;
      return !CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(beforePath, afterPath);
    }

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

    @Override
    public String toString() {
      return super.toString() + ", changelist: " + changelistId;
    }
  }
}
