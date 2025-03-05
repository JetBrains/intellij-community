// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea;

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolder;
import com.intellij.notification.NotificationAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.SelectFilePathsDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.VcsBackgroundTask;
import com.intellij.vcsUtil.VcsUtil;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.RENAME_FAILED;

/**
 * Listens to VFS events (such as adding or deleting bunch of files) and performs necessary operations with the VCS.
 */
public final class HgVFSListener extends VcsVFSListener {

  private final VcsDirtyScopeManager dirtyScopeManager;
  private static final Logger LOG = Logger.getInstance(HgVFSListener.class);

  private HgVFSListener(@NotNull HgVcs vcs, @NotNull CoroutineScope activeScope) {
    super(vcs, activeScope);

    dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  public static @NotNull HgVFSListener createInstance(@NotNull HgVcs vcs, @NotNull CoroutineScope activeScope) {
    HgVFSListener listener = new HgVFSListener(vcs, activeScope);
    listener.installListeners();
    return listener;
  }

  @Override
  protected @NotNull String getAddTitle() {
    return HgBundle.message("hg4idea.add.title");
  }

  @Override
  protected @NotNull String getSingleFileAddTitle() {
    return HgBundle.message("hg4idea.add.single.title");
  }

  @SuppressWarnings("UnresolvedPropertyKey")
  @Override
  protected @NotNull String getSingleFileAddPromptTemplate() {
    return HgBundle.message("hg4idea.add.body");
  }

  @Override
  protected void executeAdd(final @NotNull List<VirtualFile> addedFiles, final @NotNull Map<VirtualFile, VirtualFile> copyFromMap) {
    saveUnsavedVcsIgnoreFiles();

    // if a file is copied from another repository, then 'hg add' should be used instead of 'hg copy'.
    // Thus here we remove such files from the copyFromMap.
    for (Iterator<Map.Entry<VirtualFile, VirtualFile>> it = copyFromMap.entrySet().iterator(); it.hasNext(); ) {
      final Map.Entry<VirtualFile, VirtualFile> entry = it.next();
      final VirtualFile rootFrom = HgUtil.getHgRootOrNull(myProject, entry.getKey());
      final VirtualFile rootTo = HgUtil.getHgRootOrNull(myProject, entry.getValue());

      if (rootTo == null || !rootTo.equals(rootFrom)) {
        it.remove();
      }
    }

    // exclude files which are added to a directory which is not version controlled
    for (Iterator<VirtualFile> it = addedFiles.iterator(); it.hasNext(); ) {
      if (HgUtil.getHgRootOrNull(myProject, it.next()) == null) {
        it.remove();
      }
    }

    // exclude files which are ignored in .hgignore in background and execute adding after that
    final Map<VirtualFile, Collection<VirtualFile>> sortedFiles = HgUtil.sortByHgRoots(myProject, addedFiles);
    final HashSet<VirtualFile> untrackedFiles = new HashSet<>();
    new Task.Backgroundable(myProject, HgBundle.message("hg4idea.progress.checking.ignored"), false) {
      @Override
      public void run(@NotNull ProgressIndicator pi) {
        for (Map.Entry<VirtualFile, Collection<VirtualFile>> e : sortedFiles.entrySet()) {
          VirtualFile repo = e.getKey();
          final Collection<VirtualFile> files = e.getValue();
          pi.setText(repo.getPresentableUrl());
          Collection<VirtualFile> untrackedForRepo = new HgStatusCommand.Builder(false).unknown(true).removed(true).build(myProject)
            .getFiles(repo, new ArrayList<>(files));
          untrackedFiles.addAll(untrackedForRepo);
        }
        addedFiles.retainAll(untrackedFiles);
        // select files to add if there is something to select
        if (!addedFiles.isEmpty() || !copyFromMap.isEmpty()) {
          performAddingWithConfirmation(addedFiles, copyFromMap);
        }
      }
    }.queue();
  }

  @NotNull
  VcsRepositoryIgnoredFilesHolder getIgnoreRepoHolder(@NotNull VirtualFile repoRoot) {
    return Objects.requireNonNull(HgUtil.getRepositoryManager(myProject).getRepositoryForRootQuick(repoRoot)).getIgnoredFilesHolder();
  }

  @Override
  protected void performAdding(final @NotNull Collection<VirtualFile> addedFiles,
                               final @NotNull Map<VirtualFile, VirtualFile> copiedFilesFrom) {
    Map<VirtualFile, VirtualFile> copyFromMap = new HashMap<>(copiedFilesFrom);
    (new Task.Backgroundable(myProject,
                             HgBundle.message("hg4idea.add.progress"),
                             false) {
      @Override
      public void run(@NotNull ProgressIndicator aProgressIndicator) {
        final ArrayList<VirtualFile> adds = new ArrayList<>();
        final HashMap<VirtualFile, VirtualFile> copies = new HashMap<>(); // from -> to
        //delete unversioned and ignored files from copy source
        LOG.assertTrue(myProject != null, "Project is null");
        Collection<VirtualFile> unversionedAndIgnoredFiles = new ArrayList<>();
        final Map<VirtualFile, Collection<VirtualFile>> sortedSourceFilesByRepos = HgUtil.sortByHgRoots(myProject, copyFromMap.values());
        HgStatusCommand statusCommand = new HgStatusCommand.Builder(false).unknown(true).ignored(true).build(myProject);
        for (Map.Entry<VirtualFile, Collection<VirtualFile>> entry : sortedSourceFilesByRepos.entrySet()) {
          Set<HgChange> changes =
            statusCommand.executeInCurrentThread(entry.getKey(), ContainerUtil.map(entry.getValue(),
                                                                                   virtualFile -> VcsUtil.getFilePath(virtualFile)));
          for (HgChange change : changes) {
            unversionedAndIgnoredFiles.add(change.afterFile().toFilePath().getVirtualFile());
          }
        }
        copyFromMap.values().removeAll(unversionedAndIgnoredFiles);

        // separate adds from copies
        for (VirtualFile file : addedFiles) {
          final VirtualFile copyFrom = copyFromMap.get(file);
          if (copyFrom != null) {
            copies.put(copyFrom, file);
          }
          else {
            adds.add(file);
          }
        }

        // add for all files at once
        if (!adds.isEmpty()) {
          new HgAddCommand(myProject).executeInCurrentThread(adds);
        }

        // copy needs to be run for each file separately
        if (!copies.isEmpty()) {
          for (Map.Entry<VirtualFile, VirtualFile> copy : copies.entrySet()) {
            new HgCopyCommand(myProject).executeInCurrentThread(copy.getKey(), copy.getValue());
          }
        }

        for (VirtualFile file : addedFiles) {
          if (file.isDirectory()) {
            dirtyScopeManager.dirDirtyRecursively(file);
          }
          else {
            dirtyScopeManager.fileDirty(file);
          }
        }
      }
    }).queue();
  }

  @Override
  protected @NotNull String getDeleteTitle() {
    return HgBundle.message("hg4idea.remove.multiple.title");
  }

  @Override
  protected String getSingleFileDeleteTitle() {
    return HgBundle.message("hg4idea.remove.single.title");
  }

  @SuppressWarnings("UnresolvedPropertyKey")
  @Override
  protected String getSingleFileDeletePromptTemplate() {
    return HgBundle.message("hg4idea.remove.single.body");
  }

  @Override
  protected boolean shouldIgnoreDeletion(@NotNull FileStatus status) {
    return status == FileStatus.UNKNOWN;
  }

  @Override
  protected void executeDelete() {
    List<FilePath> filesToConfirmDeletion = myProcessor.acquireDeletedFiles();

    // skip files which are not under Mercurial
    skipNotUnderHg(filesToConfirmDeletion);

    skipVcsIgnored(filesToConfirmDeletion);

    List<FilePath> filesToDelete = new ArrayList<>();

    // newly added files (which were added to the repo but never committed) should be removed from the VCS,
    // but without user confirmation.
    for (Iterator<FilePath> it = filesToConfirmDeletion.iterator(); it.hasNext(); ) {
      FilePath filePath = it.next();
      Change fileChange = ChangeListManager.getInstance(myProject).getChange(filePath);
      if (fileChange != null && fileChange.getFileStatus().equals(FileStatus.ADDED)) {
        filesToDelete.add(filePath);
        it.remove();
      }
    }

    new Task.Backgroundable(myProject,
                            HgBundle.message("hg4idea.remove.progress"),
                            false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        // confirm removal from the VCS if needed
        if (myRemoveOption.getValue() != VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY) {
          if (myRemoveOption.getValue() == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY || filesToConfirmDeletion.isEmpty()) {
            filesToDelete.addAll(filesToConfirmDeletion);
          }
          else {
            final AtomicReference<Collection<FilePath>> filePaths = new AtomicReference<>();
            ApplicationManager.getApplication().invokeAndWait(() -> filePaths.set(selectFilePathsToDelete(filesToConfirmDeletion)));
            if (filePaths.get() != null) {
              filesToDelete.addAll(filePaths.get());
            }
          }
        }

        if (!filesToDelete.isEmpty()) {
          performDeletion(filesToDelete);
        }
      }
    }.queue();
  }

  private void skipVcsIgnored(@NotNull List<FilePath> filePaths) {
    Map<VirtualFile, Collection<FilePath>> groupFilePathsByHgRoots = HgUtil.groupFilePathsByHgRoots(myProject, filePaths);
    List<FilePath> ignored = groupFilePathsByHgRoots.entrySet().stream()
      .map(entry -> getIgnoreRepoHolder(entry.getKey()).removeIgnoredFiles(entry.getValue()))
      .flatMap(Collection::stream).toList();
    filePaths.removeAll(ignored);
  }

  /**
   * Changes the given collection of files by filtering out unversioned files and
   * files which are not under Mercurial repository.
   *
   * @param filesToFilter files to be filtered.
   */
  private void skipNotUnderHg(Collection<FilePath> filesToFilter) {
    for (Iterator<FilePath> iter = filesToFilter.iterator(); iter.hasNext(); ) {
      final FilePath filePath = iter.next();
      if (HgUtil.getHgRootOrNull(myProject, filePath) == null) {
        iter.remove();
      }
    }
  }

  @Override
  protected void performDeletion(final @NotNull List<FilePath> filesToDelete) {
    List<HgFile> deletes = new ArrayList<>();
    for (FilePath file : filesToDelete) {
      VirtualFile root = VcsUtil.getVcsRootFor(myProject, file);
      if (root != null) {
        deletes.add(new HgFile(root, file));
      }
    }

    if (!deletes.isEmpty()) {
      new HgRemoveCommand(myProject).executeInCurrentThread(deletes);
    }

    for (HgFile file : deletes) {
      dirtyScopeManager.fileDirty(file.toFilePath());
    }
  }

  @Override
  protected void performMoveRename(@NotNull List<MovedFileInfo> movedFiles) {
    final List<MovedFileInfo> failedToMove = new ArrayList<>();
    (new VcsBackgroundTask<>(myProject,
                             HgBundle.message("hg4idea.move.progress"),
                             movedFiles) {
      @Override
      public void onFinished() {
        if (!failedToMove.isEmpty()) {
          handleRenameError();
        }
      }

      private void handleRenameError() {
        NotificationAction viewFilesAction =
          NotificationAction.createSimple(VcsBundle.messagePointer("action.NotificationAction.VFSListener.text.view.files"), () -> {
            List<FilePath> filePaths = ContainerUtil.map(failedToMove, movedInfo -> movedInfo.getOldPath());
            DialogWrapper dialog = new ProcessedFilePathsDialog(myProject, filePaths);
            dialog.setTitle(HgBundle.message("hg4idea.rename.error.title"));
            dialog.show();
          });
        NotificationAction retryAction =
          NotificationAction.createSimpleExpiring(HgBundle.message("retry"), () -> performMoveRename(failedToMove));
        VcsNotifier.getInstance(myProject)
          .notifyError(RENAME_FAILED,
                       HgBundle.message("hg4idea.rename.error"),
                       HgBundle.message("hg4idea.rename.error.msg"),
                       viewFilesAction, retryAction);
      }

      @Override
      protected void process(final MovedFileInfo file) {
        final FilePath source = file.getOldPath();
        final FilePath target = file.getNewPath();
        VirtualFile sourceRoot = VcsUtil.getVcsRootFor(myProject, source);
        VirtualFile targetRoot = VcsUtil.getVcsRootFor(myProject, target);
        if (sourceRoot != null && sourceRoot.equals(targetRoot)) {
          HgCommandResult result;
          int attempt = 0;
          do {
            result = new HgMoveCommand(myProject).execute(sourceRoot, source, target);
          }
          while (HgErrorUtil.isWLockError(result) && attempt++ < 2);
          if (!HgErrorUtil.hasErrorsInCommandExecution(result)) {
            dirtyScopeManager.fileDirty(source);
            dirtyScopeManager.fileDirty(target);
          }
          else {
            failedToMove.add(file);
            LOG.warn("Hg rename failed:" + result.getRawError());
          }
        }
      }
    }).queue();
  }

  @Override
  protected boolean isRecursiveDeleteSupported() {
    return true;
  }

  private static class ProcessedFilePathsDialog extends SelectFilePathsDialog {

    ProcessedFilePathsDialog(@NotNull Project project, @NotNull List<FilePath> files) {
      super(project, files, null, null, null, null, false);
    }

    @Override
    protected Action @NotNull [] createActions() {
      return new Action[]{getOKAction()};
    }
  }
}
