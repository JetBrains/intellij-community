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
import com.intellij.openapi.util.SystemInfo;
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
import com.intellij.util.Function;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class LocalFileSystemImpl extends LocalFileSystemBase implements ApplicationComponent {
  private final JBReentrantReadWriteLock LOCK = LockFactory.createReadWriteLock();
  final JBLock WRITE_LOCK = LOCK.writeLock();

  private static class TreeNode {
    WatchRequestImpl watchRequest = null;
    Map<String, TreeNode> nodes = new HashMap<String, TreeNode>();
  }
  private final List<WatchRequestImpl> myRootsToWatch = new ArrayList<WatchRequestImpl>();
  private WatchRequest[] myCachedNormalizedRequests = null;
  private TreeNode myCachedNormalizedTree = null;

  private final FileWatcher myWatcher;

  private final LocalFileSystemBase myNativeFileSystem;

  private static class WatchRequestImpl implements WatchRequest {
    public final String myRootPath;

    public String myFSRootPath;
    public final boolean myToWatchRecursively;
    boolean myDominated;

    public WatchRequestImpl(String rootPath, final boolean toWatchRecursively) {
      myToWatchRecursively = toWatchRecursively;
      final int index = rootPath.indexOf(JarFileSystem.JAR_SEPARATOR);
      if (index >= 0) rootPath = rootPath.substring(0, index);
      final File file = new File(rootPath.replace('/', File.separatorChar));
      // if index >= 0 this must be a file - saves a filesystem request
      if (index >= 0 || !file.isDirectory()) {
        final File parentFile = file.getParentFile();
        if (parentFile != null) {
          if (SystemInfo.isFileSystemCaseSensitive) {
            myFSRootPath = parentFile.getAbsolutePath(); // fixes problem with symlinks under Unix (however does not under Windows!)
          }
          else {
            try {
              myFSRootPath = parentFile.getCanonicalPath();
            }
            catch (IOException e) {
              myFSRootPath = rootPath; //need something
            }
          }
        }
        else {
          myFSRootPath = rootPath.replace('/', File.separatorChar);
        }

        myRootPath = myFSRootPath.replace(File.separatorChar, '/');
      }
      else {
        myRootPath = rootPath.replace(File.separatorChar, '/');
        myFSRootPath = rootPath.replace('/', File.separatorChar);
      }
    }

    @Override
    @NotNull
    public String getRootPath() {
      return myRootPath;
    }

    @Override
    @NotNull
    public String getFileSystemRootPath() {
      return myFSRootPath;
    }

    @Override
    public boolean isToWatchRecursively() {
      return myToWatchRecursively;
    }

    @Override
    public boolean dominates(@NotNull WatchRequest other) {
      if (myToWatchRecursively) {
        return other.getRootPath().startsWith(myRootPath);
      }

      return !other.isToWatchRecursively() && myRootPath.equals(other.getRootPath());
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
    myNativeFileSystem = null;
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

    // grand VFS refresh significantly slows down local tests and generally not needed
    //ApplicationManager.getApplication().runWriteAction(new Runnable() {
    //  public void run() {
    //    refresh(false);
    //  }
    //});

    myRootsToWatch.clear();

    final File file = new File(FileUtil.getTempDirectory());
    String path = file.getCanonicalPath().replace(File.separatorChar, '/');
    addRootToWatch(path, true);
  }

  private WatchRequest[] normalizeRootsForRefresh() {
    if (myCachedNormalizedRequests != null) return myCachedNormalizedRequests;
    final List<WatchRequestImpl> result = new ArrayList<WatchRequestImpl>();

    // No need to call for a read action here since we're only called with it on hands already.
    WRITE_LOCK.lock();
    try {
      TreeNode rootNode = new TreeNode();
      for (WatchRequestImpl request : myRootsToWatch) {
        String rootPath = request.getRootPath();
        TreeNode currentNode = rootNode;
        MainLoop:
        for (String subPath : rootPath.split("/")) {
          if (!SystemInfo.isFileSystemCaseSensitive) {
            subPath = subPath.toLowerCase();
          }
          TreeNode nextNode = currentNode.nodes.get(subPath);
          if (nextNode != null) {
            currentNode = nextNode;
            if (currentNode.watchRequest != null && currentNode.watchRequest.isToWatchRecursively()) {
              // A parent path of this request is already being watched recursively. We do not need to add this one
              request.myDominated = true;
              break MainLoop;
            }
          } else {
            TreeNode newNode = new TreeNode();
            currentNode.nodes.put(subPath, newNode);
            currentNode = newNode;
          }
        }
        if (currentNode.watchRequest == null) {
          currentNode.watchRequest = request;
        } else {
          // We already have a watchRequest configured. Select the better of the two
          if (!currentNode.watchRequest.isToWatchRecursively()) {
            currentNode.watchRequest.myDominated = true;
            currentNode.watchRequest = request;
          } else {
            request.myDominated = true;
          }
        }

        if (currentNode.watchRequest.isToWatchRecursively() && currentNode.nodes.size() > 0) {
          // Since we are watching this node recursively, we can remove it's children
          visitTree(currentNode, new Function<TreeNode, Void>() {
            @Override
            public Void fun(TreeNode node) {
              if (node.watchRequest != null) {
                node.watchRequest.myDominated = true;
              }
              return null;
            }
          });
          currentNode.nodes.clear();
        }
      }
      visitTree(rootNode, new Function<TreeNode, Void>() {
        @Override
        public Void fun(TreeNode node) {
          if (node.watchRequest != null)
            result.add(node.watchRequest);
          return null;
        }
      });
      myCachedNormalizedTree = rootNode;
    }
    finally {
      WRITE_LOCK.unlock();
    }

    myCachedNormalizedRequests = result.toArray(new WatchRequest[result.size()]);
    return myCachedNormalizedRequests;
  }

  private void visitTree(TreeNode rootNode, Function<TreeNode, Void> fun) {
    for (TreeNode node : rootNode.nodes.values()) {
      fun.fun(node);
      visitTree(node, fun);
    }
  }

  private void storeRefreshStatusToFiles() {
    if (FileWatcher.getInstance().isOperational()) {
      // TODO: different ways to mark dirty for all these cases
      markPathsDirty(FileWatcher.getInstance().getDirtyPaths());
      markFlatDirsDirty(FileWatcher.getInstance().getDirtyDirs());
      markRecursiveDirsDirty(FileWatcher.getInstance().getDirtyRecursivePaths());
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
          WRITE_LOCK.lock();
          try {
            final WatchRequest[] watchRequests = normalizeRootsForRefresh();
            List<String> myRecursiveRoots = new ArrayList<String>();
            List<String> myFlatRoots = new ArrayList<String>();

            for (WatchRequest root : watchRequests) {
              if (root.isToWatchRecursively()) {
                myRecursiveRoots.add(root.getFileSystemRootPath());
              }
              else {
                myFlatRoots.add(root.getFileSystemRootPath());
              }
            }

            myWatcher.setWatchRoots(myRecursiveRoots, myFlatRoots);
          }
          finally {
            WRITE_LOCK.unlock();
          }
        }
      });
    }
  }

  private class StoreRefreshStatusThread extends Thread {
    private static final long PERIOD = 1000;

    public StoreRefreshStatusThread() {
      //noinspection HardCodedStringLiteral
      super("StoreRefreshStatusThread");
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
    if (rootPath.length() == 0 || !FileWatcher.getInstance().isOperational()) return null;

    Application app = ApplicationManager.getApplication();
    return app.runReadAction(new Computable<WatchRequest>() {
      @Override
      public WatchRequest compute() {
        WRITE_LOCK.lock();
        try {
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
        finally {
          WRITE_LOCK.unlock();
        }
      }
    });
  }

  private boolean isAlreadyWatched(final WatchRequest request) {
    if (myCachedNormalizedRequests == null) {
      normalizeRootsForRefresh();
    }
    if (myCachedNormalizedTree != null) {
      String rootPath = request.getRootPath();
      TreeNode currentNode = myCachedNormalizedTree;
      for (String subPath : rootPath.split("/")) {
        if (!SystemInfo.isFileSystemCaseSensitive) {
          subPath = subPath.toLowerCase();
        }
        TreeNode nextNode = currentNode.nodes.get(subPath);
        if (nextNode == null) {
          return false;
        }
        currentNode = nextNode;
        if (currentNode.watchRequest != null && currentNode.watchRequest.isToWatchRecursively()) {
          return true;
        }
      }
      // If we reach here it means that the exact path is already present in the graph.
      // Then this request is assumed to be present only if it is not being watched recursively.
      return !request.isToWatchRecursively() && currentNode.watchRequest != null;
    }
    return false;
  }

  @Override
  @NotNull
  public Set<WatchRequest> addRootsToWatch(@NotNull final Collection<String> rootPaths, final boolean toWatchRecursively, final boolean shouldUpdateFileWatcher) {
    if (!FileWatcher.getInstance().isOperational()) return Collections.emptySet();

    final Set<WatchRequest> result = new HashSet<WatchRequest>();
    final Set<VirtualFile> filesToSynchronize = new HashSet<VirtualFile>();

    Application application = ApplicationManager.getApplication();
    application.runReadAction(new Runnable() {
      public void run() {
        WRITE_LOCK.lock();
        try {
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
          if (shouldUpdateFileWatcher) {
            setUpFileWatcher();
          }
        }
        finally {
          WRITE_LOCK.unlock();
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
        WRITE_LOCK.lock();
        try {
          if (myRootsToWatch.remove((WatchRequestImpl)watchRequest) && !((WatchRequestImpl)watchRequest).myDominated) {
            myCachedNormalizedRequests = null;
            setUpFileWatcher();
          }
        }
        finally {
          WRITE_LOCK.unlock();
        }
      }
    });
  }

  @Override
  public void removeWatchedRoots(@NotNull final Collection<WatchRequest> rootsToWatch, final boolean forceUpdateFileWatcher) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        WRITE_LOCK.lock();
        try {
          boolean rootsChanged = false;
          if (myRootsToWatch.removeAll(rootsToWatch)) {
            myCachedNormalizedRequests = null;
            rootsChanged = true;
          }
          if (rootsChanged || forceUpdateFileWatcher) {
            setUpFileWatcher();
          }
        }
        finally {
          WRITE_LOCK.unlock();
        }
      }
    });
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @NonNls
  public String toString() {
    return "LocalFileSystem";
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
  public boolean exists(@NotNull final VirtualFile fileOrDirectory) {
    if (myNativeFileSystem == null) return super.exists(fileOrDirectory);
    else return myNativeFileSystem.exists(fileOrDirectory);
  }

  @Override
  public long getTimeStamp(@NotNull final VirtualFile file) {
    if (myNativeFileSystem == null) return super.getTimeStamp(file);
    else return myNativeFileSystem.getTimeStamp(file);
  }

  @Override
  public boolean isDirectory(@NotNull final VirtualFile file) {
    if (myNativeFileSystem == null) return super.isDirectory(file);
    else return myNativeFileSystem.isDirectory(file);
  }

  @Override
  public boolean isWritable(@NotNull final VirtualFile file) {
    if (myNativeFileSystem == null) return super.isWritable(file);
    else return myNativeFileSystem.isWritable(file);
  }

  @Override
  @NotNull
  public String[] list(@NotNull final VirtualFile file) {
    if (myNativeFileSystem == null) return super.list(file);
    else return myNativeFileSystem.list(file);
  }
}
