// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.vfs;

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolder;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ObjectsConvertor;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsVFSListener;
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.ObjectUtils;
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

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.containers.ContainerUtil.*;

public class GitVFSListener extends VcsVFSListener {
  /**
   * More than zero if events are suppressed
   */
  private final AtomicInteger myEventsSuppressLevel = new AtomicInteger(0);
  private final Git myGit;
  private final GitVcsConsoleWriter myVcsConsoleWriter;

  public GitVFSListener(@NotNull Project project, @NotNull GitVcs vcs, @NotNull  Git git, @NotNull GitVcsConsoleWriter vcsConsoleWriter) {
    super(project, vcs);
    myGit = git;
    myVcsConsoleWriter = vcsConsoleWriter;
  }

  /**
   * Set events suppressed, the events should be unsuppressed later
   *
   * @param value true if events should be suppressed, false otherwise
   */
  public void setEventsSuppressed(boolean value) {
    if (value) {
      myEventsSuppressLevel.incrementAndGet();
    }
    else {
      int v = myEventsSuppressLevel.decrementAndGet();
      assert v >= 0;
    }
  }

  @Override
  protected boolean isEventIgnored(@NotNull VirtualFileEvent event) {
    return super.isEventIgnored(event) || myEventsSuppressLevel.get() != 0;
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
    saveUnsavedVcsIgnoreFiles();
    // Filter added files before further processing
    Map<VirtualFile, List<VirtualFile>> sortedFiles;
    try {
      sortedFiles = GitUtil.sortFilesByGitRoot(addedFiles, true);
    }
    catch (VcsException e) {
      throw new RuntimeException("The exception is not expected here", e);
    }
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

        AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> originalExecuteAdd(addedFiles, copiedFiles));
      }
    });
  }

  @NotNull
  private VcsRepositoryIgnoredFilesHolder getIgnoreRepoHolder(@NotNull VirtualFile repoRoot) {
    return ObjectUtils.assertNotNull(GitUtil.getRepositoryManager(myProject).getRepositoryForRootQuick(repoRoot)).getIgnoredFilesHolder();
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

  private GitVcs gitVcs() {
    return ((GitVcs)myVcs);
  }

  private void performAdding(Collection<FilePath> filesToAdd) {
    performBackgroundOperation(filesToAdd, GitBundle.getString("add.adding"), new LongOperationPerRootExecutor() {
      @Override
      public void execute(@NotNull VirtualFile root, @NotNull List<FilePath> files) throws VcsException {
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
      Set<File> filesToRefresh = newHashSet();

      @Override
      public void execute(@NotNull VirtualFile root, @NotNull List<FilePath> files) throws VcsException {
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
    List<FilePath> toAdd = newArrayList();
    List<FilePath> toRemove = newArrayList();
    List<MovedFileInfo> toForceMove = newArrayList();
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
          List<FilePath> dirtyPaths = newArrayList();
          List<File> toRefresh = newArrayList();
          //perform adding
          for (Map.Entry<VirtualFile, List<FilePath>> toAddEntry : GitUtil.sortFilePathsByGitRoot(toAdd, true).entrySet()) {
            List<FilePath> files = toAddEntry.getValue();
            executeAdding(toAddEntry.getKey(), files);
            dirtyPaths.addAll(files);
          }
          //perform deletion
          for (Map.Entry<VirtualFile, List<FilePath>> toRemoveEntry : GitUtil.sortFilePathsByGitRoot(toRemove, true).entrySet()) {
            List<FilePath> paths = toRemoveEntry.getValue();
            toRefresh.addAll(executeDeletion(toRemoveEntry.getKey(), paths));
            dirtyPaths.addAll(paths);
          }
          //perform force move if needed
          Map<FilePath, MovedFileInfo> filesToForceMove = map2Map(toForceMove, info -> Pair.create(VcsUtil.getFilePath(info.myNewPath), info));
          dirtyPaths.addAll(map(toForceMove, fileInfo -> VcsUtil.getFilePath(fileInfo.myOldPath)));
          for (Map.Entry<VirtualFile, List<FilePath>> toForceMoveEntry : GitUtil.sortFilePathsByGitRoot(filesToForceMove.keySet(), true).entrySet()) {
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

  private void executeAdding(@NotNull VirtualFile root, @NotNull List<FilePath> files)
    throws VcsException {
    LOG.debug("Git: adding files: " + files);
    GitFileUtils.addPaths(myProject, root, files);
  }

  private Set<File> executeDeletion(@NotNull VirtualFile root, @NotNull List<FilePath> files)
    throws VcsException {
    GitFileUtils.deletePaths(myProject, root, files, "--ignore-unmatch", "--cached");
    Set<File> filesToRefresh = newHashSet();
    File rootFile = new File(root.getPath());
    for (FilePath p : files) {
      for (File f = p.getIOFile(); f != null && !FileUtil.filesEqual(f, rootFile); f = f.getParentFile()) {
        filesToRefresh.add(f);
      }
    }

    return filesToRefresh;
  }

  private Set<File> executeForceMove(@NotNull VirtualFile root,
                                     @NotNull List<FilePath> files,
                                     @NotNull Map<FilePath, MovedFileInfo> filesToMove) {
    Set<File> toRefresh = newHashSet();
    for (FilePath file : files) {
      MovedFileInfo info = filesToMove.get(file);
      GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.MV);
      h.addParameters("-f", info.myOldPath, info.myNewPath);
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

  private void performBackgroundOperation(@NotNull Collection<FilePath> files,
                                          @NotNull String operationTitle,
                                          @NotNull LongOperationPerRootExecutor executor) {
    Map<VirtualFile, List<FilePath>> sortedFiles;
    try {
      sortedFiles = GitUtil.sortFilePathsByGitRoot(files, true);
    }
    catch (VcsException e) {
      myVcsConsoleWriter.showMessage(e.getMessage());
      return;
    }

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
    void execute(@NotNull VirtualFile root, @NotNull List<FilePath> files) throws VcsException;
    Collection<File> getFilesToRefresh();
  }

}
