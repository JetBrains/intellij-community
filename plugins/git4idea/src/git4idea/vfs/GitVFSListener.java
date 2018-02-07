/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package git4idea.vfs;

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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsFileUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.i18n.GitBundle;
import git4idea.util.GitFileUtils;
import git4idea.util.GitVcsConsoleWriter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.intellij.util.containers.ContainerUtil.map2Map;
import static com.intellij.util.containers.ContainerUtil.newHashSet;

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
  protected boolean isEventIgnored(VirtualFileEvent event, boolean putInDirty) {
    return super.isEventIgnored(event, putInDirty) || myEventsSuppressLevel.get() != 0;
  }

  protected String getAddTitle() {
    return GitBundle.getString("vfs.listener.add.title");
  }

  protected String getSingleFileAddTitle() {
    return GitBundle.getString("vfs.listener.add.single.title");
  }

  protected String getSingleFileAddPromptTemplate() {
    return GitBundle.getString("vfs.listener.add.single.prompt");
  }

  @Override
  protected void executeAdd(final List<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copiedFiles) {
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

  /**
   * The version of execute add before overriding
   *
   * @param addedFiles  the added files
   * @param copiedFiles the copied files
   */
  private void originalExecuteAdd(List<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copiedFiles) {
    super.executeAdd(addedFiles, copiedFiles);
  }

  protected void performAdding(final Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap) {
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
        LOG.debug("Git: adding files: " + files);
        GitFileUtils.addPaths(myProject, root, files);
        VcsFileUtil.markFilesDirty(myProject, files);
      }

      @Override
      public Collection<File> getFilesToRefresh() {
        return Collections.emptyList();
      }
    });
  }

  protected String getDeleteTitle() {
    return GitBundle.getString("vfs.listener.delete.title");
  }

  protected String getSingleFileDeleteTitle() {
    return GitBundle.getString("vfs.listener.delete.single.title");
  }

  protected String getSingleFileDeletePromptTemplate() {
    return GitBundle.getString("vfs.listener.delete.single.prompt");
  }

  protected void performDeletion(final List<FilePath> filesToDelete) {
    performBackgroundOperation(filesToDelete, GitBundle.getString("remove.removing"), new LongOperationPerRootExecutor() {
      HashSet<File> filesToRefresh = new HashSet<>();

      public void execute(@NotNull VirtualFile root, @NotNull List<FilePath> files) throws VcsException {
        GitFileUtils.delete(myProject, root, files, "--ignore-unmatch", "--cached");
        if (!myProject.isDisposed()) {
          VcsFileUtil.markFilesDirty(myProject, files);
        }
        File rootFile = new File(root.getPath());
        for (FilePath p : files) {
          for (File f = p.getIOFile(); f != null && !FileUtil.filesEqual(f, rootFile); f = f.getParentFile()) {
            filesToRefresh.add(f);
          }
        }
      }

      public Collection<File> getFilesToRefresh() {
        return filesToRefresh;
      }
    });
  }

  protected void performMoveRename(final List<MovedFileInfo> movedFiles) {
    List<FilePath> toAdd = ContainerUtil.newArrayList();
    List<FilePath> toRemove = ContainerUtil.newArrayList();
    List<MovedFileInfo> toForceMove = ContainerUtil.newArrayList();
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
    performAdding(toAdd);
    performDeletion(toRemove);
    performForceMove(toForceMove);
  }

  private void performForceMove(@NotNull List<MovedFileInfo> files) {
    Map<FilePath, MovedFileInfo> filesToMove = map2Map(files, (info) -> Pair.create(VcsUtil.getFilePath(info.myNewPath), info));
    Set<File> toRefresh = newHashSet();
    performBackgroundOperation(filesToMove.keySet(), "Moving Files...", new LongOperationPerRootExecutor() {
      @Override
      public void execute(@NotNull VirtualFile root, @NotNull List<FilePath> files) {
        for (FilePath file : files) {
          MovedFileInfo info = filesToMove.get(file);
          GitLineHandler h = new GitLineHandler(myProject, root, GitCommand.MV);
          h.addParameters("-f", info.myOldPath, info.myNewPath);
          myGit.runCommand(h);
          toRefresh.add(new File(info.myOldPath));
          toRefresh.add(new File(info.myNewPath));
        }
      }

      @Override
      public Collection<File> getFilesToRefresh() {
        return toRefresh;
      }
    });
  }

  protected boolean isDirectoryVersioningSupported() {
    return false;
  }

  @Override
  protected Collection<FilePath> selectFilePathsToDelete(final List<FilePath> deletedFiles) {
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
