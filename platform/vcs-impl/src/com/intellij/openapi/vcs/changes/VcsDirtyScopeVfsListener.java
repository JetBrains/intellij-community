// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.vcsUtil.VcsUtil;
import com.intellij.vfs.AsyncVfsEventsListener;
import com.intellij.vfs.AsyncVfsEventsPostProcessor;
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;
import java.util.Objects;

/**
 * Listens to file system events and notifies VcsDirtyScopeManagers responsible for changed files to mark these files dirty.
 */
public class VcsDirtyScopeVfsListener implements AsyncVfsEventsListener, Disposable {
  private final @NotNull Project myProject;

  private boolean myForbid; // for tests only

  public VcsDirtyScopeVfsListener(@NotNull Project project) {
    myProject = project;
    AsyncVfsEventsPostProcessor.getInstance().addListener(this, this);
  }

  public static VcsDirtyScopeVfsListener getInstance(@NotNull Project project) {
    return project.getService(VcsDirtyScopeVfsListener.class);
  }

  public static void install(@NotNull Project project) {
    getInstance(project);
  }

  @TestOnly
  public void setForbid(boolean forbid) {
    myForbid = forbid;
  }

  @Override
  public void dispose() {
  }

  @TestOnly
  public void waitForAsyncTaskCompletion() {
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed();
  }

  @Override
  public void filesChanged(@NotNull List<? extends @NotNull VFileEvent> events) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    if (myForbid || !vcsManager.hasActiveVcss()) return;

    final FilesAndDirs dirtyFilesAndDirs = new FilesAndDirs();
    // collect files and directories - sources of events
    for (VFileEvent event : events) {
      ProgressManager.checkCanceled();

      final boolean isDirectory;
      if (event instanceof VFileCreateEvent) {
        if (!((VFileCreateEvent)event).getParent().isInLocalFileSystem()) {
          continue;
        }
        isDirectory = ((VFileCreateEvent)event).isDirectory();
      }
      else {
        final VirtualFile file = Objects.requireNonNull(event.getFile(), "All events but VFileCreateEvent have @NotNull getFile()");
        if (!file.isInLocalFileSystem()) {
          continue;
        }
        isDirectory = file.isDirectory();
      }

      if (event instanceof VFileMoveEvent) {
        add(vcsManager, dirtyFilesAndDirs, VcsUtil.getFilePath(((VFileMoveEvent)event).getOldPath(), isDirectory));
        add(vcsManager, dirtyFilesAndDirs, VcsUtil.getFilePath(((VFileMoveEvent)event).getNewPath(), isDirectory));
      }
      else if (event instanceof VFilePropertyChangeEvent && ((VFilePropertyChangeEvent)event).isRename()) {
        // if a file was renamed, then the file is dirty and its parent directory is dirty too;
        // if a directory was renamed, all its children are recursively dirty, the parent dir is also dirty but not recursively.
        FilePath oldPath = VcsUtil.getFilePath(((VFilePropertyChangeEvent)event).getOldPath(), isDirectory);
        FilePath newPath = VcsUtil.getFilePath(((VFilePropertyChangeEvent)event).getNewPath(), isDirectory);
        // the file is dirty recursively, its old directory is dirty alone
        addWithParentDirectory(vcsManager, dirtyFilesAndDirs, oldPath);
        add(vcsManager, dirtyFilesAndDirs, newPath);
      }
      else {
        add(vcsManager, dirtyFilesAndDirs, VcsUtil.getFilePath(event.getPath(), isDirectory));
      }
    }

    VcsDirtyScopeManagerImpl dirtyScopeManager = VcsDirtyScopeManagerImpl.getInstanceImpl(myProject);
    dirtyScopeManager.fileVcsPathsDirty(dirtyFilesAndDirs.files.asMap(), dirtyFilesAndDirs.dirs.asMap());
  }

  /**
   * Stores VcsDirtyScopeManagers and files and directories which should be marked dirty by them.
   * Files will be marked dirty, directories will be marked recursively dirty, so if you need to mark dirty a directory, but
   * not recursively, you should add it to files.
   */
  private static class FilesAndDirs {
    @NotNull VcsDirtyScopeMap files = new VcsDirtyScopeMap();
    @NotNull VcsDirtyScopeMap dirs = new VcsDirtyScopeMap();
  }

  private static void add(@NotNull ProjectLevelVcsManager vcsManager,
                          @NotNull FilesAndDirs filesAndDirs,
                          @NotNull FilePath filePath,
                          boolean withParentDirectory) {
    VcsRoot vcsRoot = vcsManager.getVcsRootObjectFor(filePath);
    AbstractVcs vcs = vcsRoot != null ? vcsRoot.getVcs() : null;
    if (vcsRoot == null || vcs == null) return;

    if (filePath.isDirectory()) {
      filesAndDirs.dirs.add(vcsRoot, filePath);
    }
    else {
      filesAndDirs.files.add(vcsRoot, filePath);
    }

    if (withParentDirectory && vcs.areDirectoriesVersionedItems()) {
      FilePath parentPath = filePath.getParentPath();
      if (parentPath != null && vcsManager.getVcsFor(parentPath) == vcs) {
        filesAndDirs.files.add(vcsRoot, parentPath);
      }
    }
  }

  private static void add(@NotNull ProjectLevelVcsManager vcsManager,
                          @NotNull FilesAndDirs filesAndDirs,
                          @NotNull FilePath filePath) {
    add(vcsManager, filesAndDirs, filePath, false);
  }

  private static void addWithParentDirectory(@NotNull ProjectLevelVcsManager vcsManager,
                                             @NotNull FilesAndDirs filesAndDirs,
                                             @NotNull FilePath filePath) {
    add(vcsManager, filesAndDirs, filePath, true);
  }
}
