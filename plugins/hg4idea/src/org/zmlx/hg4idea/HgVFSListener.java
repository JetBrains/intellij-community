// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vcs.changes.ui.SelectFilePathsDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.VcsBackgroundTask;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.command.*;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.intellij.util.containers.ContainerUtil.map2List;

/**
 * Listens to VFS events (such as adding or deleting bunch of files) and performs necessary operations with the VCS.
 */
public final class HgVFSListener extends VcsVFSListener {

  private final VcsDirtyScopeManager dirtyScopeManager;
  private static final Logger LOG = Logger.getInstance(HgVFSListener.class);

  private HgVFSListener(@NotNull HgVcs vcs) {
    super(vcs);
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
  }

  @NotNull
  public static HgVFSListener createInstance(@NotNull HgVcs vcs) {
    HgVFSListener listener = new HgVFSListener(vcs);
    listener.installListeners();
    return listener;
  }

  @NotNull
  @Override
  protected String getAddTitle() {
    return HgBundle.message("hg4idea.add.title");
  }

  @NotNull
  @Override
  protected String getSingleFileAddTitle() {
    return HgBundle.message("hg4idea.add.single.title");
  }

  @SuppressWarnings("UnresolvedPropertyKey")
  @NotNull
  @Override
  protected String getSingleFileAddPromptTemplate() {
    return HgBundle.message("hg4idea.add.body");
  }

  @Override
  protected void executeAdd(@NotNull final List<VirtualFile> addedFiles, @NotNull final Map<VirtualFile, VirtualFile> copyFromMap) {
    executeAddWithoutIgnores(addedFiles, copyFromMap,
                             (notIgnoredAddedFiles, copiedFilesMap) -> originalExecuteAdd(notIgnoredAddedFiles, copiedFilesMap));
  }

  @Override
  protected void executeAddWithoutIgnores(@NotNull List<VirtualFile> addedFiles,
                                          @NotNull Map<VirtualFile, VirtualFile> copyFromMap,
                                          @NotNull ExecuteAddCallback executeAddCallback) {
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

          AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> executeAddCallback.executeAdd(addedFiles, copyFromMap));
        }
      }
    }.queue();
  }

  @NotNull
  VcsRepositoryIgnoredFilesHolder getIgnoreRepoHolder(@NotNull VirtualFile repoRoot) {
    return Objects.requireNonNull(HgUtil.getRepositoryManager(myProject).getRepositoryForRootQuick(repoRoot)).getIgnoredFilesHolder();
  }
  /**
   * The version of execute add before overriding
   *
   * @param addedFiles  the added files
   * @param copiedFiles the copied files
   */
  private void originalExecuteAdd(List<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copiedFiles) {
    super.executeAdd(addedFiles, copiedFiles);
  }

  @Override
  protected void performAdding(@NotNull final Collection<VirtualFile> addedFiles, @NotNull final Map<VirtualFile, VirtualFile> copiedFilesFrom) {
    Map<VirtualFile, VirtualFile> copyFromMap = new HashMap<>(copiedFilesFrom);
    (new Task.ConditionalModal(myProject,
                               HgBundle.message("hg4idea.add.progress"),
                               false,
                               VcsConfiguration.getInstance(myProject).getAddRemoveOption() ) {
      @Override public void run(@NotNull ProgressIndicator aProgressIndicator) {
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
          } else {
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

  @NotNull
  @Override
  protected String getDeleteTitle() {
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

  @NotNull
  @Override
  protected VcsDeleteType needConfirmDeletion(@NotNull final VirtualFile file) {
    return ChangeListManagerImpl.getInstanceImpl(myProject).getUnversionedFiles().contains(file)
           ? VcsDeleteType.IGNORE
           : VcsDeleteType.CONFIRM;
  }

  @Override
  protected void executeDelete() {
    AllDeletedFiles files = myProcessor.acquireAllDeletedFiles();
    List<FilePath> filesToDelete = files.deletedWithoutConfirmFiles;
    List<FilePath> filesToConfirmDeletion = files.deletedFiles;

    // skip files which are not under Mercurial
    skipNotUnderHg(filesToDelete);
    skipNotUnderHg(filesToConfirmDeletion);

    filesToDelete.removeAll(processAndGetVcsIgnored(filesToDelete));
    filesToConfirmDeletion.removeAll(processAndGetVcsIgnored(filesToConfirmDeletion));

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

    new Task.ConditionalModal(myProject,
                              HgBundle.message("hg4idea.remove.progress"),
                              false,
                              VcsConfiguration.getInstance(myProject).getAddRemoveOption()) {
      @Override public void run( @NotNull ProgressIndicator indicator ) {
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

  @NotNull
  private List<FilePath> processAndGetVcsIgnored(@NotNull List<FilePath> filePaths) {
    Map<VirtualFile, Collection<FilePath>> groupFilePathsByHgRoots = HgUtil.groupFilePathsByHgRoots(myProject, filePaths);
    return groupFilePathsByHgRoots.entrySet().stream()
      .map(entry -> getIgnoreRepoHolder(entry.getKey()).removeIgnoredFiles(entry.getValue()))
      .flatMap(Collection::stream).collect(Collectors.toList());
  }

  /**
   * Changes the given collection of files by filtering out unversioned files and
   * files which are not under Mercurial repository.
   *
   * @param filesToFilter    files to be filtered.
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
  protected void performDeletion(@NotNull final List<FilePath> filesToDelete) {
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
    (new VcsBackgroundTask<MovedFileInfo>(myProject,
                                          HgBundle.message("hg4idea.move.progress"),
                                          VcsConfiguration.getInstance(myProject).getAddRemoveOption(),
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
          DialogWrapper dialog =
            new ProcessedFilePathsDialog(myProject, map2List(failedToMove, movedInfo -> VcsUtil.getFilePath(movedInfo.myOldPath)));
          dialog.setTitle(HgBundle.message("hg4idea.rename.error.title"));
          dialog.show();
        });
        NotificationAction retryAction = NotificationAction.createSimpleExpiring(HgBundle.message("retry"), () -> performMoveRename(failedToMove));
        VcsNotifier.getInstance(myProject)
          .notifyError(HgBundle.message("hg4idea.rename.error"), HgBundle.message("hg4idea.rename.error.msg"), viewFilesAction, retryAction);
      }

      @Override
      protected void process(final MovedFileInfo file) {
        final FilePath source = VcsUtil.getFilePath(file.myOldPath);
        final FilePath target = VcsUtil.getFilePath(file.myNewPath);
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
  protected boolean isDirectoryVersioningSupported() {
    return false;
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
