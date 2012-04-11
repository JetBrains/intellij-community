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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
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

    if (application.isDisposeInProgress()) return;

    if (myWatcher.isOperational()) {
      application.runReadAction(new Runnable() {
        @Override
        public void run() {
          synchronized (myLock) {
            final WatchRequestImpl[] watchRequests = normalizeRootsForRefresh();
            List<String> myRecursiveRoots = new ArrayList<String>();
            List<String> myFlatRoots = new ArrayList<String>();

            for (WatchRequestImpl root : watchRequests) {
              if (root.isToWatchRecursively()) {
                myRecursiveRoots.add(root.myFSRootPath);
              }
              else {
                myFlatRoots.add(root.myFSRootPath);
              }
            }

            myWatcher.setWatchRoots(myRecursiveRoots, myFlatRoots);
          }
        }
      });
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

  @Override
  @NotNull
  public String getComponentName() {
    return "LocalFileSystem";
  }

  @Override
  public WatchRequest addRootToWatch(@NotNull final String rootPath, final boolean toWatchRecursively) {
    if (rootPath.length() == 0 || !myWatcher.isOperational()) return null;

    Application app = ApplicationManager.getApplication();
    return app.runReadAction(new Computable<WatchRequest>() {
      @Override
      public WatchRequest compute() {
        synchronized (myLock) {
          final WatchRequestImpl result = new WatchRequestImpl(rootPath, toWatchRecursively);
          boolean alreadyWatched = isAlreadyWatched(result);
          if (!alreadyWatched) {
            final VirtualFile existingFile = findFileByPathIfCached(rootPath);
            if (existingFile != null) {
              final ModalityState modalityState = ModalityState.defaultModalityState();
              RefreshQueue.getInstance().refresh(true, toWatchRecursively, null, modalityState, existingFile);
              if (existingFile.isDirectory() && !toWatchRecursively && existingFile instanceof NewVirtualFile) {
                for (VirtualFile child : ((NewVirtualFile)existingFile).getCachedChildren()) {
                  RefreshQueue.getInstance().refresh(true, false, null, modalityState, child);
                }
              }
            }
          }
          myRootsToWatch.add(result);
          if (alreadyWatched) {
            result.myDominated = true;
            return result;
          }
          myCachedNormalizedRequests = null;
          setUpFileWatcher();
          return result;
        }
      }
    });
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
  public Set<WatchRequest> addRootsToWatch(@NotNull final Collection<String> rootPaths, final boolean toWatchRecursively) {
    if (!myWatcher.isOperational()) return Collections.emptySet();

    final Set<WatchRequest> result = new HashSet<WatchRequest>();
    final Set<VirtualFile> filesToSynchronize = new HashSet<VirtualFile>();

    Application application = ApplicationManager.getApplication();
    application.runReadAction(new Runnable() {
      public void run() {
        synchronized (myLock) {
          for (String rootPath : rootPaths) {
            LOG.assertTrue(rootPath != null);
            if (rootPath.length() > 0) {
              final WatchRequestImpl request = new WatchRequestImpl(rootPath, toWatchRecursively);
              final VirtualFile existingFile = findFileByPathIfCached(rootPath);
              if (existingFile != null) {
                if (!isAlreadyWatched(request)) {
                  filesToSynchronize.add(existingFile);
                }
              }
              result.add(request);
              myRootsToWatch.add(request); //add in any case, safe to add inplace without copying myRootsToWatch before the loop
            }
          }
          myCachedNormalizedRequests = null;
          setUpFileWatcher();
        }
      }
    });

    if (!application.isUnitTestMode() && !filesToSynchronize.isEmpty()) {
      for (VirtualFile file : filesToSynchronize) {
        if (file instanceof NewVirtualFile && file.getFileSystem() instanceof LocalFileSystem) {
          ((NewVirtualFile)file).markDirtyRecursively();
        }
      }
      refreshFiles(filesToSynchronize, true, toWatchRecursively, null);
    }

    return result;
  }

  @Override
  public void removeWatchedRoot(@NotNull final WatchRequest watchRequest) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (myLock) {
          if (myRootsToWatch.remove((WatchRequestImpl)watchRequest) && !((WatchRequestImpl)watchRequest).myDominated) {
            myCachedNormalizedRequests = null;
            setUpFileWatcher();
          }
        }
      }
    });
  }

  @Override
  public void removeWatchedRoots(@NotNull final Collection<WatchRequest> rootsToWatch) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (myLock) {
          if (myRootsToWatch.removeAll(rootsToWatch)) {
            myCachedNormalizedRequests = null;
            setUpFileWatcher();
          }
        }
      }
    });
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
