/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class LocalFileSystemImpl extends LocalFileSystemBase implements ApplicationComponent {
  private final Object myLock = new Object();
  private final List<WatchRequestImpl> myRootsToWatch = new ArrayList<WatchRequestImpl>();
  private WatchRequestImpl[] myCachedNormalizedRequests = null;
  private final FileWatcher myWatcher;

  private static class WatchRequestImpl implements WatchRequest {
    private final String myRootPath;
    private final boolean myToWatchRecursively;
    private String myFSRootPath;
    private boolean myDominated;

    public WatchRequestImpl(String rootPath, final boolean toWatchRecursively) {
      final int index = rootPath.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (index >= 0) rootPath = rootPath.substring(0, index);

      File rootFile = new File(FileUtil.toSystemDependentName(rootPath));
      if (index > 0 || !rootFile.isDirectory()) {
        rootFile = rootFile.getParentFile();
        assert rootFile != null : rootPath;
      }

      myFSRootPath = rootFile.getAbsolutePath();
      myRootPath = FileUtil.toSystemIndependentName(myFSRootPath);
      myToWatchRecursively = toWatchRecursively;
    }

    @Override
    @NotNull
    public String getRootPath() {
      return myRootPath;
    }

    /** @deprecated implementation details (to remove in IDEA 13) */
    @Override
    @NotNull
    public String getFileSystemRootPath() {
      return myFSRootPath;
    }

    @Override
    public boolean isToWatchRecursively() {
      return myToWatchRecursively;
    }

    /** @deprecated implementation details (to remove in IDEA 13) */
    @Override
    public boolean dominates(@NotNull WatchRequest other) {
      return LocalFileSystemImpl.dominates(this, (WatchRequestImpl)other);
    }

    @Override
    public String toString() {
      return myRootPath;
    }
  }

  public LocalFileSystemImpl() {
    myWatcher = FileWatcher.getInstance();
    if (myWatcher.isOperational()) {
      new StoreRefreshStatusThread().start();
    }
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "LocalFileSystem";
  }

  @TestOnly
  public void cleanupForNextTest(Set<VirtualFile> survivors) throws IOException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        FileDocumentManager.getInstance().saveAllDocuments();
      }
    });
    ((PersistentFS)ManagingFS.getInstance()).clearIdCache();

    for (VirtualFile root : ManagingFS.getInstance().getRoots(this)) {
      if (root instanceof VirtualDirectoryImpl) {
        ((VirtualDirectoryImpl)root).cleanupCachedChildren(survivors);
      }
    }

    myRootsToWatch.clear();
  }

  private WatchRequestImpl[] normalizeRootsForRefresh() {
    if (myCachedNormalizedRequests != null) return myCachedNormalizedRequests;
    List<WatchRequestImpl> result = new ArrayList<WatchRequestImpl>();

    // No need to call for a read action here since we're only called with it on hands already.
    synchronized (myLock) {
      NextRoot:
      for (WatchRequestImpl request : myRootsToWatch) {
        String rootPath = request.getRootPath();
        boolean recursively = request.isToWatchRecursively();

        for (Iterator<WatchRequestImpl> iterator1 = result.iterator(); iterator1.hasNext();) {
          final WatchRequestImpl otherRequest = iterator1.next();
          final String otherRootPath = otherRequest.getRootPath();
          final boolean otherRecursively = otherRequest.isToWatchRecursively();
          if ((rootPath.equals(otherRootPath) && (!recursively || otherRecursively)) ||
              (FileUtil.startsWith(rootPath, otherRootPath) && otherRecursively)) {
            continue NextRoot;
          }
          else if (FileUtil.startsWith(otherRootPath, rootPath) && (recursively || !otherRecursively)) {
            otherRequest.myDominated = true;
            iterator1.remove();
          }
        }
        result.add(request);
        request.myDominated = false;
      }
    }

    myCachedNormalizedRequests = result.toArray(new WatchRequestImpl[result.size()]);
    return myCachedNormalizedRequests;
  }

  private void storeRefreshStatusToFiles() {
    if (myWatcher.isOperational()) {
      // TODO: different ways to mark dirty for all these cases
      markPathsDirty(myWatcher.getDirtyPaths());
      markFlatDirsDirty(myWatcher.getDirtyDirs());
      markRecursiveDirsDirty(myWatcher.getDirtyRecursivePaths());
    }
  }

  private void markPathsDirty(final List<String> dirtyFiles) {
    for (String dirtyFile : dirtyFiles) {
      String path = dirtyFile.replace(File.separatorChar, '/');
      VirtualFile file = findFileByPathIfCached(path);
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markDirty();
      }
    }
  }

  private void markFlatDirsDirty(final List<String> dirtyFiles) {
    for (String dirtyFile : dirtyFiles) {
      String path = dirtyFile.replace(File.separatorChar, '/');
      VirtualFile file = findFileByPathIfCached(path);
      if (file instanceof NewVirtualFile) {
        final NewVirtualFile nvf = (NewVirtualFile)file;
        nvf.markDirty();
        for (VirtualFile child : nvf.getCachedChildren()) {
          ((NewVirtualFile)child).markDirty();
        }
      }
    }
  }

  private void markRecursiveDirsDirty(final List<String> dirtyFiles) {
    for (String dirtyFile : dirtyFiles) {
      String path = dirtyFile.replace(File.separatorChar, '/');
      VirtualFile file = findFileByPathIfCached(path);
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markDirtyRecursively();
      }
    }
  }

  public void markSuspiciousFilesDirty(List<VirtualFile> files) {
    storeRefreshStatusToFiles();

    if (myWatcher.isOperational()) {
      for (String root : myWatcher.getManualWatchRoots()) {
        final VirtualFile suspiciousRoot = findFileByPathIfCached(root);
        if (suspiciousRoot != null) {
          ((NewVirtualFile)suspiciousRoot).markDirtyRecursively();
        }
      }
    }
    else {
      for (VirtualFile file : files) {
        if (file.getFileSystem() == this) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
      }
    }
  }

  private void setUpFileWatcher() {
    final Application application = ApplicationManager.getApplication();
    if (application.isDisposeInProgress() || !myWatcher.isOperational()) return;

    final AccessToken token = application.acquireReadActionLock();
    try {
      synchronized (myLock) {
        final WatchRequestImpl[] watchRequests = normalizeRootsForRefresh();
        final List<String> myRecursiveRoots = new ArrayList<String>();
        final List<String> myFlatRoots = new ArrayList<String>();

        for (WatchRequestImpl watchRequest : watchRequests) {
          if (watchRequest.isToWatchRecursively()) {
            myRecursiveRoots.add(watchRequest.myFSRootPath);
          }
          else {
            myFlatRoots.add(watchRequest.myFSRootPath);
          }
        }

        myWatcher.setWatchRoots(myRecursiveRoots, myFlatRoots);
      }
    }
    finally {
      token.finish();
    }
  }

  private class StoreRefreshStatusThread extends Thread {
    private static final long PERIOD = 1000;

    public StoreRefreshStatusThread() {
      super(StoreRefreshStatusThread.class.getSimpleName());
      setPriority(MIN_PRIORITY);
      setDaemon(true);
    }

    @Override
    public void run() {
      while (true) {
        final Application application = ApplicationManager.getApplication();
        if (application == null || application.isDisposed()) break;
        
        storeRefreshStatusToFiles();
        TimeoutUtil.sleep(PERIOD);
      }
    }
  }

  private boolean isAlreadyWatched(final WatchRequestImpl request) {
    for (final WatchRequestImpl current : normalizeRootsForRefresh()) {
      if (dominates(current, request)) return true;
    }
    return false;
  }

  private static boolean dominates(final WatchRequestImpl request, final WatchRequestImpl other) {
    if (request.myToWatchRecursively) {
      return other.myRootPath.startsWith(request.myRootPath);
    }

    return !other.myToWatchRecursively && request.myRootPath.equals(other.myRootPath);
  }

  @Override
  @NotNull
  public Set<WatchRequest> addRootsToWatch(@NotNull final Collection<String> rootPaths, final boolean watchRecursively) {
    if (rootPaths.isEmpty() || !myWatcher.isOperational()) {
      return Collections.emptySet();
    }
    else {
      return replaceWatchedRoots(Collections.<WatchRequest>emptySet(), rootPaths, watchRecursively);
    }
  }

  @Override
  public void removeWatchedRoots(@NotNull final Collection<WatchRequest> watchRequests) {
    if (watchRequests.isEmpty()) return;

    final AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      synchronized (myLock) {
        final boolean update = doRemoveWatchedRoots(watchRequests);
        if (update) {
          myCachedNormalizedRequests = null;
          setUpFileWatcher();
        }
      }
    }
    finally {
      token.finish();
    }
  }

  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull final Collection<WatchRequest> watchRequests,
                                               @NotNull final Collection<String> rootPaths,
                                               final boolean watchRecursively) {
    if (rootPaths.isEmpty() || !myWatcher.isOperational()) {
      removeWatchedRoots(watchRequests);
      return Collections.emptySet();
    }

    final Set<WatchRequest> result = new HashSet<WatchRequest>();
    final Set<VirtualFile> filesToSync = new HashSet<VirtualFile>();

    final AccessToken token = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      synchronized (myLock) {
        final boolean update = doAddRootsToWatch(rootPaths, watchRecursively, result, filesToSync) ||
                               doRemoveWatchedRoots(watchRequests);
        if (update) {
          myCachedNormalizedRequests = null;
          setUpFileWatcher();
        }
      }
    }
    finally {
      token.finish();
    }

    syncFiles(filesToSync, watchRecursively);

    return result;
  }

  private boolean doAddRootsToWatch(@NotNull final Collection<String> roots,
                                    final boolean recursively,
                                    @NotNull final Set<WatchRequest> results,
                                    @NotNull final Set<VirtualFile> filesToSync) {
    boolean update = false;

    for (String root : roots) {
      final WatchRequestImpl result = new WatchRequestImpl(root, recursively);
      final boolean alreadyWatched = isAlreadyWatched(result);

      if (!alreadyWatched) {
        final VirtualFile existingFile = findFileByPathIfCached(root);
        if (existingFile != null) {
          if (existingFile.isDirectory() && !recursively && existingFile instanceof NewVirtualFile) {
            filesToSync.addAll(((NewVirtualFile)existingFile).getCachedChildren());
          }
        }
      }
      result.myDominated = alreadyWatched;
      myRootsToWatch.add(result);
      results.add(result);

      update |= !alreadyWatched;
    }

    return update;
  }

  private void syncFiles(@NotNull final Set<VirtualFile> filesToSync, final boolean watchRecursively) {
    if (filesToSync.isEmpty() || ApplicationManager.getApplication().isUnitTestMode()) return;

    for (VirtualFile file : filesToSync) {
      if (file instanceof NewVirtualFile && file.getFileSystem() instanceof LocalFileSystem) {
        ((NewVirtualFile)file).markDirtyRecursively();
      }
    }
    refreshFiles(filesToSync, true, watchRecursively, null);
  }

  private boolean doRemoveWatchedRoots(@NotNull final Collection<WatchRequest> watchRequests) {
    boolean update = false;

    for (WatchRequest watchRequest : watchRequests) {
      final boolean wasWatched = myRootsToWatch.remove((WatchRequestImpl)watchRequest) && !((WatchRequestImpl)watchRequest).myDominated;
      update |= wasWatched;
    }

    return update;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    Runnable heavyRefresh = new Runnable() {
      @Override
      public void run() {
        for (VirtualFile root : ManagingFS.getInstance().getRoots(LocalFileSystemImpl.this)) {
          ((NewVirtualFile)root).markDirtyRecursively();
        }

        refresh(asynchronous);
      }
    };

    if (asynchronous && myWatcher.isOperational()) {
      RefreshQueue.getInstance().refresh(true, true, heavyRefresh, ManagingFS.getInstance().getRoots(this));
    }
    else {
      heavyRefresh.run();
    }
  }

  @Override
  public FileAttributes getAttributes(@NotNull final VirtualFile file) {
    return FileSystemUtil.getAttributes(FileUtil.toSystemDependentName(file.getPath()));
  }

  @NonNls
  public String toString() {
    return "LocalFileSystem";
  }
}
