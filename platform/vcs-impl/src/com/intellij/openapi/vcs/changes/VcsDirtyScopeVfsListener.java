// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ZipperUpdater;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.util.Alarm;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Listens to file system events and notifies VcsDirtyScopeManagers responsible for changed files to mark these files dirty.
 */
public class VcsDirtyScopeVfsListener implements AsyncFileListener, Disposable {
  @NotNull private final ProjectLevelVcsManager myVcsManager;

  private boolean myForbid; // for tests only

  @NotNull private final ZipperUpdater myZipperUpdater;
  private final List<FilesAndDirs> myQueue;
  private final Object myLock;
  @NotNull private final Runnable myDirtReporter;

  public VcsDirtyScopeVfsListener(@NotNull Project project) {
    myVcsManager = ProjectLevelVcsManager.getInstance(project);

    VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(project);

    myLock = new Object();
    myQueue = new ArrayList<>();
    myDirtReporter = () -> {
      ArrayList<FilesAndDirs> list;
      synchronized (myLock) {
        list = new ArrayList<>(myQueue);
        myQueue.clear();
      }

      List<FilePath> dirtyFiles = new ArrayList<>();
      List<FilePath> dirtyDirs = new ArrayList<>();
      for (FilesAndDirs filesAndDirs : list) {
        for (Set<FilePath> value : filesAndDirs.files.asMap().values()) {
          dirtyFiles.addAll(value);
        }
        for (Set<FilePath> value : filesAndDirs.dirs.asMap().values()) {
          dirtyDirs.addAll(value);
        }
      }

      if (!dirtyFiles.isEmpty() || !dirtyDirs.isEmpty()) {
        dirtyScopeManager.filePathsDirty(dirtyFiles, dirtyDirs);
      }
    };
    myZipperUpdater = new ZipperUpdater(300, Alarm.ThreadToUse.POOLED_THREAD, this);
    Disposer.register(project, this);
    VirtualFileManager.getInstance().addAsyncFileListener(this, project);
  }

  public static VcsDirtyScopeVfsListener getInstance(@NotNull Project project) {
    return project.getService(VcsDirtyScopeVfsListener.class);
  }

  public static void install(@NotNull Project project) {
    getInstance(project);
  }

  public void setForbid(boolean forbid) {
    assert ApplicationManager.getApplication().isUnitTestMode();
    myForbid = forbid;
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      myQueue.clear();
    }

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      //noinspection TestOnlyProblems
      waitForAsyncTaskCompletion();
    }
  }

  @TestOnly
  public void waitForAsyncTaskCompletion() {
    myZipperUpdater.waitForAllExecuted(10, TimeUnit.SECONDS);
  }

  @Nullable
  @Override
  public ChangeApplier prepareChange(@NotNull List<? extends @NotNull VFileEvent> events) {
    if (myForbid || !myVcsManager.hasActiveVcss()) return null;
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
        add(myVcsManager, dirtyFilesAndDirs, VcsUtil.getFilePath(((VFileMoveEvent)event).getOldPath(), isDirectory));
        add(myVcsManager, dirtyFilesAndDirs, VcsUtil.getFilePath(((VFileMoveEvent)event).getNewPath(), isDirectory));
      }
      else if (event instanceof VFilePropertyChangeEvent && ((VFilePropertyChangeEvent)event).isRename()) {
        // if a file was renamed, then the file is dirty and its parent directory is dirty too;
        // if a directory was renamed, all its children are recursively dirty, the parent dir is also dirty but not recursively.
        FilePath oldPath = VcsUtil.getFilePath(((VFilePropertyChangeEvent)event).getOldPath(), isDirectory);
        FilePath newPath = VcsUtil.getFilePath(((VFilePropertyChangeEvent)event).getNewPath(), isDirectory);
        // the file is dirty recursively, its old directory is dirty alone
        addWithParentDirectory(myVcsManager, dirtyFilesAndDirs, oldPath);
        add(myVcsManager, dirtyFilesAndDirs, newPath);
      }
      else {
        add(myVcsManager, dirtyFilesAndDirs, VcsUtil.getFilePath(event.getPath(), isDirectory));
      }
    }

    return new ChangeApplier() {
      @Override
      public void afterVfsChange() {
        markDirtyOnPooled(dirtyFilesAndDirs);
      }
    };
  }

  private void markDirtyOnPooled(@NotNull FilesAndDirs dirtyFilesAndDirs) {
    if (!dirtyFilesAndDirs.isEmpty()) {
      synchronized (myLock) {
        myQueue.add(dirtyFilesAndDirs);
      }
      myZipperUpdater.queue(myDirtReporter);
    }
  }

  /**
   * Stores VcsDirtyScopeManagers and files and directories which should be marked dirty by them.
   * Files will be marked dirty, directories will be marked recursively dirty, so if you need to mark dirty a directory, but
   * not recursively, you should add it to files.
   */
  private static class FilesAndDirs {
    @NotNull VcsDirtyScopeMap files = new VcsDirtyScopeMap();
    @NotNull VcsDirtyScopeMap dirs = new VcsDirtyScopeMap();

    private boolean isEmpty() {
      return files.isEmpty() && dirs.isEmpty();
    }
  }

  private static void add(@NotNull ProjectLevelVcsManager vcsManager,
                          @NotNull FilesAndDirs filesAndDirs,
                          @NotNull FilePath filePath,
                          boolean withParentDirectory) {
    AbstractVcs vcs = vcsManager.getVcsFor(filePath);
    if (vcs == null) return;

    if (filePath.isDirectory()) {
      filesAndDirs.dirs.add(vcs, filePath);
    }
    else {
      filesAndDirs.files.add(vcs, filePath);
    }

    if (withParentDirectory && vcs.areDirectoriesVersionedItems()) {
      FilePath parentPath = filePath.getParentPath();
      if (parentPath != null && vcsManager.getVcsFor(parentPath) == vcs) {
        filesAndDirs.files.add(vcs, parentPath);
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
