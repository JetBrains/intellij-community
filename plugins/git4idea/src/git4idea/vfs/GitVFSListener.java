// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.vfs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.AppUIUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.i18n.GitBundle;
import git4idea.util.GitFileUtils;
import git4idea.util.GitVcsConsoleWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtil.map2Map;

public class GitVFSListener extends VcsVFSListener {
  private final Git myGit;
  private final GitVcsConsoleWriter myVcsConsoleWriter;

  private GitVFSListener(@NotNull GitVcs vcs, @NotNull Git git, @NotNull GitVcsConsoleWriter vcsConsoleWriter) {
    super(vcs);
    myGit = git;
    myVcsConsoleWriter = vcsConsoleWriter;
  }

  @NotNull
  public static GitVFSListener createInstance(@NotNull GitVcs vcs,
                                              @NotNull Git git,
                                              @NotNull GitVcsConsoleWriter vcsConsoleWriter) {
    GitVFSListener listener = new GitVFSListener(vcs, git, vcsConsoleWriter);
    listener.installListeners();
    return listener;
  }

  @NotNull
  @Override
  protected String getAddTitle() {
    return GitBundle.getString("vfs.listener.add.title");
  }

  @NotNull
  @Override
  protected String getSingleFileAddTitle() {
    return GitBundle.getString("vfs.listener.add.single.title");
  }

  @NotNull
  @Override
  protected String getSingleFileAddPromptTemplate() {
    return GitBundle.getString("vfs.listener.add.single.prompt");
  }

  @Override
  protected void executeAdd(@NotNull final List<VirtualFile> addedFiles, @NotNull final Map<VirtualFile, VirtualFile> copiedFiles) {
    executeAddWithoutIgnores(addedFiles, copiedFiles,
                             (notIgnoredAddedFiles, copiedFilesMap) -> originalExecuteAdd(notIgnoredAddedFiles, copiedFilesMap));
  }

  @Override
  protected void executeAddWithoutIgnores(@NotNull List<VirtualFile> addedFiles,
                                          @NotNull Map<VirtualFile, VirtualFile> copyFromMap,
                                          @NotNull ExecuteAddCallback executeAddCallback) {
    saveUnsavedVcsIgnoreFiles();
    // Filter added files before further processing
    Map<VirtualFile, List<VirtualFile>> sortedFiles = GitUtil.sortFilesByGitRootIgnoringMissing(myProject, addedFiles);
    final HashSet<VirtualFile> retainedFiles = new HashSet<>();
    final ProgressManager progressManager = ProgressManager.getInstance();
    progressManager.run(new Task.Backgroundable(myProject, GitBundle.getString("vfs.listener.checking.ignored"), true) {
      @Override
      public void run(@NotNull ProgressIndicator pi) {
        for (Map.Entry<VirtualFile, List<VirtualFile>> e : sortedFiles.entrySet()) {
          VirtualFile root = e.getKey();
          List<VirtualFile> files = e.getValue();
          pi.setText(root.getPresentableUrl());
          try {
            retainedFiles.addAll(myGit.untrackedFiles(myProject, root, files));
          }
          catch (VcsException ex) {
            myVcsConsoleWriter.showMessage(ex.getMessage());
          }
        }
        addedFiles.retainAll(retainedFiles);

        AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> executeAddCallback.executeAdd(addedFiles, copyFromMap));
      }
    });
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
  protected void performAdding(@NotNull final Collection<VirtualFile> addedFiles, @NotNull final Map<VirtualFile, VirtualFile> copyFromMap) {
    // copied files (copyFromMap) are ignored, because they are included into added files.
    performAdding(ObjectsConvertor.vf2fp(new ArrayList<>(addedFiles)));
  }

  private void performAdding(Collection<? extends FilePath> filesToAdd) {
    performBackgroundOperation(filesToAdd, GitBundle.getString("add.adding"), new LongOperationPerRootExecutor() {
      @Override
      public void execute(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files) throws VcsException {
        executeAdding(root, files);
        if (!myProject.isDisposed()) {
          VcsFileUtil.markFilesDirty(myProject, files);
        }
      }

      @Override
      public Collection<File> getFilesToRefresh() {
        return Collections.emptyList();
      }
    });
  }

  @NotNull
  @Override
  protected String getDeleteTitle() {
    return GitBundle.getString("vfs.listener.delete.title");
  }

  @Override
  protected String getSingleFileDeleteTitle() {
    return GitBundle.getString("vfs.listener.delete.single.title");
  }

  @Override
  protected String getSingleFileDeletePromptTemplate() {
    return GitBundle.getString("vfs.listener.delete.single.prompt");
  }

  @Override
  protected void performDeletion(@NotNull final List<FilePath> filesToDelete) {
    performBackgroundOperation(filesToDelete, GitBundle.getString("remove.removing"), new LongOperationPerRootExecutor() {
      final Set<File> filesToRefresh = new HashSet<>();

      @Override
      public void execute(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files) throws VcsException {
        filesToRefresh.addAll(executeDeletion(root, files));
        if (!myProject.isDisposed()) {
          VcsFileUtil.markFilesDirty(myProject, files);
        }
      }

      @Override
      public Collection<File> getFilesToRefresh() {
        return filesToRefresh;
      }
    });
  }

  @Override
  protected void performMoveRename(@NotNull final List<MovedFileInfo> movedFiles) {
    List<FilePath> toAdd = new ArrayList<>();
    List<FilePath> toRemove = new ArrayList<>();
    List<MovedFileInfo> toForceMove = new ArrayList<>();
    for (MovedFileInfo movedInfo : movedFiles) {
      String oldPath = movedInfo.myOldPath;
      String newPath = movedInfo.myNewPath;
      if (!SystemInfo.isFileSystemCaseSensitive && GitUtil.isCaseOnlyChange(oldPath, newPath)) {
        toForceMove.add(movedInfo);
      }
      else {
        toRemove.add(VcsUtil.getFilePath(oldPath));
        toAdd.add(VcsUtil.getFilePath(newPath));
      }
    }
    LOG.debug("performMoveRename. \ntoAdd: " + toAdd + "\ntoRemove: " + toRemove + "\ntoForceMove: " + toForceMove);
    GitVcs.runInBackground(new Task.Backgroundable(myProject, "Moving Files...") {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<FilePath> dirtyPaths = new ArrayList<>();
          List<File> toRefresh = new ArrayList<>();
          //perform adding
          for (Map.Entry<VirtualFile, List<FilePath>> toAddEntry : GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, toAdd).entrySet()) {
            List<FilePath> files = toAddEntry.getValue();
            executeAdding(toAddEntry.getKey(), files);
            dirtyPaths.addAll(files);
          }
          //perform deletion
          for (Map.Entry<VirtualFile, List<FilePath>> toRemoveEntry : GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, toRemove).entrySet()) {
            List<FilePath> paths = toRemoveEntry.getValue();
            toRefresh.addAll(executeDeletion(toRemoveEntry.getKey(), paths));
            dirtyPaths.addAll(paths);
          }
          //perform force move if needed
          Map<FilePath, MovedFileInfo> filesToForceMove = map2Map(toForceMove, info -> Pair.create(VcsUtil.getFilePath(info.myNewPath), info));
          dirtyPaths.addAll(map(toForceMove, fileInfo -> VcsUtil.getFilePath(fileInfo.myOldPath)));
          for (Map.Entry<VirtualFile, List<FilePath>> toForceMoveEntry : GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, filesToForceMove.keySet()).entrySet()) {
            List<FilePath> paths = toForceMoveEntry.getValue();
            toRefresh.addAll(executeForceMove(toForceMoveEntry.getKey(), paths, filesToForceMove));
            dirtyPaths.addAll(paths);
          }

          VcsFileUtil.markFilesDirty(myProject, dirtyPaths);
          RefreshVFsSynchronously.refreshFiles(toRefresh);
        }
        catch (VcsException ex) {
          myVcsConsoleWriter.showMessage(ex.getMessage());
        }
      }
    });
  }

  private void executeAdding(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files)
    throws VcsException {
    LOG.debug("Git: adding files: " + files);
    GitFileUtils.addPaths(myProject, root, files);
  }

  private Set<File> executeDeletion(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files)
    throws VcsException {
    GitFileUtils.deletePaths(myProject, root, files, "--ignore-unmatch", "--cached");
    Set<File> filesToRefresh = new HashSet<>();
    File rootFile = new File(root.getPath());
    for (FilePath p : files) {
      for (File f = p.getIOFile(); f != null && !FileUtil.filesEqual(f, rootFile); f = f.getParentFile()) {
        filesToRefresh.add(f);
      }
    }

    return filesToRefresh;
  }

  private Set<File> executeForceMove(@NotNull VirtualFile root,
                                     @NotNull List<? extends FilePath> files,
                                     @NotNull Map<FilePath, MovedFileInfo> filesToMove) {
    Set<File> toRefresh = new HashSet<>();
    for (FilePath file : files) {
      MovedFileInfo info = filesToMove.get(file);
      GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.MV);
      h.addParameters("-f");
      h.addRelativePaths(VcsUtil.getFilePath(info.myOldPath), VcsUtil.getFilePath(info.myNewPath));
      myGit.runCommand(h);
      toRefresh.add(new File(info.myOldPath));
      toRefresh.add(new File(info.myNewPath));
    }

    return toRefresh;
  }

  @Override
  protected boolean isDirectoryVersioningSupported() {
    return false;
  }

  @Override
  protected Collection<FilePath> selectFilePathsToDelete(@NotNull final List<FilePath> deletedFiles) {
    // For git asking about vcs delete does not make much sense. The result is practically identical.
    return deletedFiles;
  }

  private void performBackgroundOperation(@NotNull Collection<? extends FilePath> files,
                                          @NotNull String operationTitle,
                                          @NotNull LongOperationPerRootExecutor executor) {
    Map<VirtualFile, List<FilePath>> sortedFiles = GitUtil.sortFilePathsByGitRootIgnoringMissing(myProject, files);

    GitVcs.runInBackground(new Task.Backgroundable(myProject, operationTitle) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        for (Map.Entry<VirtualFile, List<FilePath>> e : sortedFiles.entrySet()) {
          try {
            executor.execute(e.getKey(), e.getValue());
          }
          catch (final VcsException ex) {
            myVcsConsoleWriter.showMessage(ex.getMessage());
          }
        }
        RefreshVFsSynchronously.refreshFiles(executor.getFilesToRefresh());
      }
    });
  }

  private interface LongOperationPerRootExecutor {
    void execute(@NotNull VirtualFile root, @NotNull List<? extends FilePath> files) throws VcsException;
    Collection<File> getFilesToRefresh();
  }

  @TestOnly
  public void waitForAllEventsProcessedInTestMode() {
    assert ApplicationManager.getApplication().isUnitTestMode();
    ((ChangeListManagerImpl)myChangeListManager).waitEverythingDoneInTestMode();
    ((ExternallyAddedFilesProcessorImpl)myExternalFilesProcessor).waitForEventsProcessedInTestMode();
  }

}
