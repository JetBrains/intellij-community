// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value;
import com.intellij.openapi.vcs.VcsVFSListener;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.index.GitStageManagerKt;
import git4idea.util.GitFileUtils;
import git4idea.util.GitVcsConsoleWriter;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.map2Map;
import static git4idea.i18n.GitBundle.message;

public final class GitVFSListener extends VcsVFSListener {
  private GitVFSListener(@NotNull GitVcs vcs, @NotNull CoroutineScope coroutineScope) {
    super(vcs, coroutineScope);
  }

  public static @NotNull GitVFSListener createInstance(@NotNull GitVcs vcs, @NotNull CoroutineScope coroutineScope) {
    GitVFSListener listener = new GitVFSListener(vcs, coroutineScope);
    listener.installListeners();
    return listener;
  }

  @Override
  protected @NotNull String getAddTitle() {
    return message("vfs.listener.add.title");
  }

  @Override
  protected @NotNull String getSingleFileAddTitle() {
    return message("vfs.listener.add.single.title");
  }

  @Override
  @SuppressWarnings("UnresolvedPropertyKey")
  protected @NotNull String getSingleFileAddPromptTemplate() {
    return message("vfs.listener.add.single.prompt");
  }

  @Override
  protected void executeAdd(final @NotNull List<VirtualFile> addedFiles, final @NotNull Map<VirtualFile, VirtualFile> copyFromMap) {
    saveUnsavedVcsIgnoreFiles();

    ProgressManager.getInstance().run(new Task.Backgroundable(myProject, message("vfs.listener.checking.ignored"), true) {
      @Override
      public void run(@NotNull ProgressIndicator pi) {
        // Filter added files before further processing
        Map<VirtualFile, List<VirtualFile>> sortedFiles = GitUtil.sortFilesByGitRootIgnoringMissing(myProject, addedFiles);
        final HashSet<VirtualFile> retainedFiles = new HashSet<>();
        for (Map.Entry<VirtualFile, List<VirtualFile>> e : sortedFiles.entrySet()) {
          VirtualFile root = e.getKey();
          List<VirtualFile> files = e.getValue();
          pi.setText(root.getPresentableUrl());
          try {
            retainedFiles.addAll(Git.getInstance().untrackedFiles(myProject, root, files));
          }
          catch (VcsException ex) {
            GitVcsConsoleWriter.getInstance(myProject).showMessage(ex.getMessage());
          }
        }
        addedFiles.retainAll(retainedFiles);

        performAddingWithConfirmation(addedFiles, copyFromMap);
      }
    });
  }

  @Override
  protected void performAdding(final @NotNull Collection<VirtualFile> addedFiles,
                               final @NotNull Map<VirtualFile, VirtualFile> copyFromMap) {
    // copied files (copyFromMap) are ignored, because they are included into added files.
    performAdding(map(addedFiles, VcsUtil::getFilePath));
  }

  private void performAdding(Collection<? extends FilePath> filesToAdd) {
    performBackgroundOperation(filesToAdd, message("add.adding"), new LongOperationPerRootExecutor() {
      @Override
      public void execute(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files) throws VcsException {
        if (isStageEnabled()) {
          executeAddingToIndex(root, files);
        }
        else {
          executeAdding(root, files);
        }
        if (!myProject.isDisposed()) {
          VcsFileUtil.markFilesDirty(myProject, files);
        }
      }
    });
  }

  @Override
  protected @NotNull String getDeleteTitle() {
    return message("vfs.listener.delete.title");
  }

  @Override
  protected String getSingleFileDeleteTitle() {
    return message("vfs.listener.delete.single.title");
  }

  @Override
  @SuppressWarnings("UnresolvedPropertyKey")
  protected String getSingleFileDeletePromptTemplate() {
    return message("vfs.listener.delete.single.prompt");
  }

  @Override
  protected void performDeletion(final @NotNull List<FilePath> filesToDelete) {
    performBackgroundOperation(filesToDelete, message("remove.removing"), new LongOperationPerRootExecutor() {
      @Override
      public void execute(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files) throws VcsException {
        executeDeletion(root, files);
        if (!myProject.isDisposed()) {
          VcsFileUtil.markFilesDirty(myProject, files);
        }
      }
    });
  }

  @Override
  protected void performMoveRename(final @NotNull List<MovedFileInfo> movedFiles) {
    List<FilePath> toAdd = new ArrayList<>();
    List<FilePath> toRemove = new ArrayList<>();
    List<MovedFileInfo> toForceMove = new ArrayList<>();
    for (MovedFileInfo movedInfo : movedFiles) {
      String oldPath = movedInfo.myOldPath;
      String newPath = movedInfo.myNewPath;
      if (!movedInfo.isCaseSensitive() && GitUtil.isCaseOnlyChange(oldPath, newPath)) {
        toForceMove.add(movedInfo);
      }
      else {
        toRemove.add(movedInfo.getOldPath());
        toAdd.add(movedInfo.getNewPath());
      }
    }

    Collection<FilePath> selectedToAdd;
    Collection<FilePath> selectedToRemove;
    if (isStageEnabled()) {
      selectedToAdd = selectFilePathsToAdd(toAdd);
      selectedToRemove = selectFilePathsToDelete(toRemove);
    }
    else if (Value.DO_NOTHING_SILENTLY.equals(myRemoveOption.getValue()) &&
             Value.DO_NOTHING_SILENTLY.equals(myAddOption.getValue())) {
      selectedToAdd = Collections.emptyList();
      selectedToRemove = Collections.emptyList();
    }
    else {
      selectedToAdd = toAdd;
      selectedToRemove = toRemove;
    }
    if (toAdd.isEmpty() && toRemove.isEmpty() && toForceMove.isEmpty()) return;

    LOG.debug("performMoveRename. \ntoAdd: " + toAdd + "\ntoRemove: " + toRemove + "\ntoForceMove: " + toForceMove);
    GitVcs.runInBackground(new Task.Backgroundable(myProject, message("progress.title.moving.files")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<FilePath> dirtyPaths = new ArrayList<>();
          List<File> toRefresh = new ArrayList<>();
          //perform adding
          for (Map.Entry<VirtualFile, List<FilePath>> toAddEntry :
            GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, selectedToAdd).entrySet()) {
            List<FilePath> files = toAddEntry.getValue();
            executeAdding(toAddEntry.getKey(), files);
            dirtyPaths.addAll(files);
          }
          //perform deletion
          for (Map.Entry<VirtualFile, List<FilePath>> toRemoveEntry :
            GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, selectedToRemove).entrySet()) {
            List<FilePath> paths = toRemoveEntry.getValue();
            executeDeletion(toRemoveEntry.getKey(), paths);
            dirtyPaths.addAll(paths);
          }
          //perform force move if needed
          Map<FilePath, MovedFileInfo> filesToForceMove = map2Map(toForceMove, info -> Pair.create(info.getNewPath(), info));
          dirtyPaths.addAll(map(toForceMove, fileInfo -> fileInfo.getOldPath()));
          for (Map.Entry<VirtualFile, List<FilePath>> toForceMoveEntry :
            GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, filesToForceMove.keySet()).entrySet()) {
            List<FilePath> paths = toForceMoveEntry.getValue();
            toRefresh.addAll(executeForceMove(toForceMoveEntry.getKey(), paths, filesToForceMove));
            dirtyPaths.addAll(paths);
          }

          VcsFileUtil.markFilesDirty(myProject, dirtyPaths);
          RefreshVFsSynchronously.refreshFiles(toRefresh);
        }
        catch (VcsException ex) {
          GitVcsConsoleWriter.getInstance(myProject).showMessage(ex.getMessage());
        }
      }
    });
  }

  private void executeAdding(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files)
    throws VcsException {
    LOG.debug("Git: adding files: " + files);
    GitFileUtils.addPaths(myProject, root, files, false, false);
  }

  private void executeAddingToIndex(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files)
    throws VcsException {
    LOG.debug("Git: adding files to index: " + files);
    GitFileUtils.addPathsToIndex(myProject, root, files);
  }

  private void executeDeletion(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files)
    throws VcsException {
    GitFileUtils.deletePaths(myProject, root, files, "--ignore-unmatch", "--cached", "-r");
  }

  private Set<File> executeForceMove(@NotNull VirtualFile root,
                                     @NotNull List<? extends FilePath> files,
                                     @NotNull Map<FilePath, MovedFileInfo> filesToMove) {
    Set<File> toRefresh = new HashSet<>();
    for (FilePath file : files) {
      MovedFileInfo info = filesToMove.get(file);
      GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.MV);
      h.addParameters("-f");
      h.addRelativePaths(info.getOldPath(), info.getNewPath());
      Git.getInstance().runCommand(h);
      toRefresh.add(new File(info.myOldPath));
      toRefresh.add(new File(info.myNewPath));
    }

    return toRefresh;
  }

  private boolean isStageEnabled() {
    return GitStageManagerKt.isStagingAreaAvailable(myProject);
  }

  @Override
  protected boolean isRecursiveDeleteSupported() {
    return true;
  }

  @Override
  protected boolean isFileCopyingFromTrackingSupported() {
    return false;
  }

  @Override
  protected @NotNull List<FilePath> selectFilePathsToDelete(final @NotNull List<FilePath> deletedFiles) {
    if (isStageEnabled()) {
      return super.selectFilePathsToDelete(deletedFiles);
    }
    if (Value.DO_NOTHING_SILENTLY.equals(myRemoveOption.getValue())) {
      return Collections.emptyList();
    }
    else {
      // For git asking about vcs delete does not make much sense. The result is practically identical. Remove silently.
      return deletedFiles;
    }
  }

  private void performBackgroundOperation(@NotNull Collection<? extends FilePath> files,
                                          @NotNull @NlsContexts.ProgressTitle String operationTitle,
                                          @NotNull LongOperationPerRootExecutor executor) {
    GitVcs.runInBackground(new Task.Backgroundable(myProject, operationTitle) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, files).forEach((root, filePaths) -> {
          try {
            executor.execute(root, filePaths);
          }
          catch (final VcsException ex) {
            GitVcsConsoleWriter.getInstance(myProject).showMessage(ex.getMessage());
          }
        });
      }
    });
  }

  private interface LongOperationPerRootExecutor {
    void execute(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files) throws VcsException;
  }

  @TestOnly
  public void waitForExternalFilesEventsProcessedInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    waitForEventsProcessedInTestMode();
  }
}
