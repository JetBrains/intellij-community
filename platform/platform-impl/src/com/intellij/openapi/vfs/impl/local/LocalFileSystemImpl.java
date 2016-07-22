/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsImplUtil;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.HashSet;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class LocalFileSystemImpl extends LocalFileSystemBase implements ApplicationComponent {
  private static final String FS_ROOT = "/";
  private static final int STATUS_UPDATE_PERIOD = 1000;

  private final ManagingFS myManagingFS;
  private final FileWatcher myWatcher;

  private final Object myLock = new Object();
  private final Set<WatchRequestImpl> myRootsToWatch = new THashSet<>();
  private TreeNode myNormalizedTree;

  private static class WatchRequestImpl implements WatchRequest {
    private final String myFSRootPath;
    private final boolean myWatchRecursively;
    private boolean myDominated;

    public WatchRequestImpl(String rootPath, boolean watchRecursively) {
      myFSRootPath = rootPath;
      myWatchRecursively = watchRecursively;
    }

    @Override
    @NotNull
    public String getRootPath() {
      return FileUtil.toSystemIndependentName(myFSRootPath);
    }

    @Override
    public boolean isToWatchRecursively() {
      return myWatchRecursively;
    }

    @Override
    public String toString() {
      return getRootPath();
    }
  }

  private static class TreeNode {
    private WatchRequestImpl watchRequest;
    private final Map<String, TreeNode> nodes = new THashMap<>(1, FileUtil.PATH_HASHING_STRATEGY);
  }

  public LocalFileSystemImpl(@NotNull Application app, @NotNull ManagingFS managingFS) {
    myManagingFS = managingFS;
    myWatcher = new FileWatcher(myManagingFS);
    if (myWatcher.isOperational()) {
      JobScheduler.getScheduler().scheduleWithFixedDelay(
        () -> { if (!app.isDisposed()) storeRefreshStatusToFiles(); },
        STATUS_UPDATE_PERIOD, STATUS_UPDATE_PERIOD, TimeUnit.MILLISECONDS);
    }
  }

  @NotNull
  public FileWatcher getFileWatcher() {
    return myWatcher;
  }

  @Override
  public void initComponent() { }

  @Override
  public void disposeComponent() {
    myWatcher.dispose();
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "LocalFileSystem";
  }

  private List<WatchRequestImpl> normalizeRootsForRefresh() {
    List<WatchRequestImpl> result = new ArrayList<>();

    synchronized (myLock) {
      TreeNode rootNode = new TreeNode();
      for (WatchRequestImpl request : myRootsToWatch) {
        request.myDominated = false;
        String rootPath = request.getRootPath();

        TreeNode currentNode = rootNode;
        MainLoop:
        for (String subPath : splitPath(rootPath)) {
          TreeNode nextNode = currentNode.nodes.get(subPath);
          if (nextNode != null) {
            currentNode = nextNode;
            if (currentNode.watchRequest != null && currentNode.watchRequest.isToWatchRecursively()) {
              // a parent path of this request is already being watched recursively - do not need to add this one
              request.myDominated = true;
              break MainLoop;
            }
          }
          else {
            TreeNode newNode = new TreeNode();
            currentNode.nodes.put(subPath, newNode);
            currentNode = newNode;
          }
        }
        if (currentNode.watchRequest == null) {
          currentNode.watchRequest = request;
        }
        else {
          // we already have a watchRequest configured - select the better of the two
          if (!currentNode.watchRequest.isToWatchRecursively()) {
            currentNode.watchRequest.myDominated = true;
            currentNode.watchRequest = request;
          }
          else {
            request.myDominated = true;
          }
        }

        if (currentNode.watchRequest.isToWatchRecursively() && !currentNode.nodes.isEmpty()) {
          // since we are watching this node recursively, we can remove it's children
          visitTree(currentNode, node -> {
            if (node.watchRequest != null) {
              node.watchRequest.myDominated = true;
            }
          });
          currentNode.nodes.clear();
        }
      }

      visitTree(rootNode, node -> {
        if (node.watchRequest != null) {
          result.add(node.watchRequest);
        }
      });
      myNormalizedTree = rootNode;
    }

    return result;
  }

  @NotNull
  private static List<String> splitPath(@NotNull String path) {
    if (path.isEmpty()) {
      return Collections.emptyList();
    }

    if (FS_ROOT.equals(path)) {
      return Collections.singletonList(FS_ROOT);
    }

    List<String> parts = StringUtil.split(path, FS_ROOT);
    if (StringUtil.startsWithChar(path, '/')) {
      parts.add(0, FS_ROOT);
    }
    return parts;
  }

  private static void visitTree(TreeNode rootNode, Consumer<TreeNode> consumer) {
    for (TreeNode node : rootNode.nodes.values()) {
      consumer.consume(node);
      visitTree(node, consumer);
    }
  }

  private boolean isAlreadyWatched(final WatchRequestImpl request) {
    if (myNormalizedTree == null) {
      normalizeRootsForRefresh();
    }

    String rootPath = request.getRootPath();
    TreeNode currentNode = myNormalizedTree;
    for (String subPath : splitPath(rootPath)) {
      TreeNode nextNode = currentNode.nodes.get(subPath);
      if (nextNode == null) {
        return false;
      }
      currentNode = nextNode;
      if (currentNode.watchRequest != null && currentNode.watchRequest.isToWatchRecursively()) {
        return true;
      }
    }
    // if we reach here it means that the exact path is already present in the graph -
    // then this request is assumed to be present only if it is not being watched recursively
    return !request.isToWatchRecursively() && currentNode.watchRequest != null;
  }

  private void storeRefreshStatusToFiles() {
    if (myWatcher.isOperational()) {
      FileWatcher.DirtyPaths dirtyPaths = myWatcher.getDirtyPaths();
      markPathsDirty(dirtyPaths.dirtyPaths);
      markFlatDirsDirty(dirtyPaths.dirtyDirectories);
      markRecursiveDirsDirty(dirtyPaths.dirtyPathsRecursive);
    }
  }

  private void markPathsDirty(Iterable<String> dirtyPaths) {
    for (String dirtyPath : dirtyPaths) {
      VirtualFile file = findFileByPathIfCached(dirtyPath);
      if (file instanceof NewVirtualFile) {
        ((NewVirtualFile)file).markDirty();
      }
    }
  }

  private void markFlatDirsDirty(Iterable<String> dirtyPaths) {
    for (String dirtyPath : dirtyPaths) {
      Pair<NewVirtualFile, NewVirtualFile> pair = VfsImplUtil.findCachedFileByPath(this, dirtyPath);
      if (pair.first != null) {
        pair.first.markDirty();
        for (VirtualFile child : pair.first.getCachedChildren()) {
          ((NewVirtualFile)child).markDirty();
        }
      }
      else if (pair.second != null) {
        pair.second.markDirty();
      }
    }
  }

  private void markRecursiveDirsDirty(Iterable<String> dirtyPaths) {
    for (String dirtyPath : dirtyPaths) {
      Pair<NewVirtualFile, NewVirtualFile> pair = VfsImplUtil.findCachedFileByPath(this, dirtyPath);
      if (pair.first != null) {
        pair.first.markDirtyRecursively();
      }
      else if (pair.second != null) {
        pair.second.markDirty();
      }
    }
  }

  public void markSuspiciousFilesDirty(@NotNull List<VirtualFile> files) {
    storeRefreshStatusToFiles();

    if (myWatcher.isOperational()) {
      for (String root : myWatcher.getManualWatchRoots()) {
        VirtualFile suspiciousRoot = findFileByPathIfCached(root);
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

  @NotNull
  @Override
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequests,
                                               @Nullable Collection<String> recursiveRoots,
                                               @Nullable Collection<String> flatRoots) {
    recursiveRoots = ObjectUtils.notNull(recursiveRoots, Collections.emptyList());
    flatRoots = ObjectUtils.notNull(flatRoots, Collections.emptyList());

    Set<WatchRequest> result = new HashSet<>();
    synchronized (myLock) {
      boolean update = doAddRootsToWatch(recursiveRoots, flatRoots, result) |
                       doRemoveWatchedRoots(watchRequests);
      if (update) {
        myNormalizedTree = null;
        setUpFileWatcher();
      }
    }
    return result;
  }

  private boolean doAddRootsToWatch(Collection<String> recursiveRoots, Collection<String> flatRoots, Set<WatchRequest> results) {
    boolean update = false;

    for (String root : recursiveRoots) {
      WatchRequestImpl request = watch(root, true);
      if (request == null) continue;
      boolean alreadyWatched = isAlreadyWatched(request);

      request.myDominated = alreadyWatched;
      myRootsToWatch.add(request);
      results.add(request);

      update |= !alreadyWatched;
    }

    for (String root : flatRoots) {
      WatchRequestImpl request = watch(root, false);
      if (request == null) continue;
      boolean alreadyWatched = isAlreadyWatched(request);

      request.myDominated = alreadyWatched;
      myRootsToWatch.add(request);
      results.add(request);

      update |= !alreadyWatched;
    }

    return update;
  }

  @Nullable
  private static WatchRequestImpl watch(String rootPath, boolean recursively) {
    int index = rootPath.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (index >= 0) rootPath = rootPath.substring(0, index);

    File rootFile = new File(FileUtil.toSystemDependentName(rootPath));
    if (!rootFile.isAbsolute()) {
      LOG.warn("Invalid path: " + rootPath);
      return null;
    }

    return new WatchRequestImpl(rootFile.getAbsolutePath(), recursively);
  }

  private boolean doRemoveWatchedRoots(@NotNull Collection<WatchRequest> watchRequests) {
    boolean update = false;

    for (WatchRequest watchRequest : watchRequests) {
      WatchRequestImpl impl = (WatchRequestImpl)watchRequest;
      boolean wasWatched = myRootsToWatch.remove(impl) && !impl.myDominated;
      update |= wasWatched;
    }

    return update;
  }

  private void setUpFileWatcher() {
    if (!ApplicationManager.getApplication().isDisposeInProgress() && myWatcher.isOperational()) {
      List<String> recursiveRoots = new ArrayList<>();
      List<String> flatRoots = new ArrayList<>();

      for (WatchRequestImpl request : normalizeRootsForRefresh()) {
        (request.isToWatchRecursively() ? recursiveRoots : flatRoots).add(request.myFSRootPath);
      }

      myWatcher.setWatchRoots(recursiveRoots, flatRoots);
    }
  }

  @Override
  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    Runnable heavyRefresh = () -> {
      for (VirtualFile root : myManagingFS.getRoots(this)) {
        ((NewVirtualFile)root).markDirtyRecursively();
      }
      refresh(asynchronous);
    };

    if (asynchronous && myWatcher.isOperational()) {
      RefreshQueue.getInstance().refresh(true, true, heavyRefresh, myManagingFS.getRoots(this));
    }
    else {
      heavyRefresh.run();
    }
  }

  @Override
  public String toString() {
    return "LocalFileSystem";
  }

  @TestOnly
  public void cleanupForNextTest() {
    FileDocumentManager.getInstance().saveAllDocuments();
    PersistentFS.getInstance().clearIdCache();
    synchronized (myLock) {
      myRootsToWatch.clear();
      myNormalizedTree = null;
    }
  }
}