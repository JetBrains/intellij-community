// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem.WatchRequest;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;

/**
 * Class manages the roots to monitor via {@link FileWatcher} -- i.e., it keeps {@link FileWatcher} configured
 * with the actual set of roots to watch for.
 * Unless stated otherwise, all paths are {@link SystemIndependent @SystemIndependent}.
 */
final class WatchRootsManager {
  private static final Logger LOG = Logger.getInstance(WatchRootsManager.class);

  private final FileWatcher myFileWatcher;

  private final NavigableMap<String, List<WatchRequest>> myRecursiveWatchRoots = WatchRootsUtil.createFileNavigableMap();
  private final NavigableMap<String, List<WatchRequest>> myFlatWatchRoots = WatchRootsUtil.createFileNavigableMap();
  private final NavigableSet<String> myOptimizedRecursiveWatchRoots = WatchRootsUtil.createFileNavigableSet();
  private final NavigableMap<String, SymlinkData> mySymlinksByPath = WatchRootsUtil.createFileNavigableMap();
  private final Int2ObjectMap<SymlinkData> mySymlinksById = new Int2ObjectOpenHashMap<>();
  private final NavigableSet<Pair<String, String>> myPathMappings = WatchRootsUtil.createMappingsNavigableSet();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private boolean myWatcherRequiresUpdate;  // synchronized on `myLock`
  private final Object myLock = new Object();

  WatchRootsManager(@NotNull FileWatcher fileWatcher, @NotNull Disposable parent) {
    myFileWatcher = fileWatcher;
    ApplicationManager.getApplication().getMessageBus().connect(parent).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
      @Override
      public void after(@NotNull List<? extends @NotNull VFileEvent> events) {
        synchronized (myLock) {
          if (myWatcherRequiresUpdate) {
            updateFileWatcher();
          }
        }
      }
    });
  }

  @NotNull Set<WatchRequest> replaceWatchedRoots(
    @NotNull Collection<WatchRequest> requestsToRemove,
    @NotNull Collection<String> recursiveRootsToAdd,
    @NotNull Collection<String> flatRootsToAdd
  ) {
    Set<WatchRequest> recursiveRequestsToRemove = new HashSet<>(), flatRequestsToRemove = new HashSet<>();
    requestsToRemove.forEach(req -> (req.isToWatchRecursively() ? recursiveRequestsToRemove : flatRequestsToRemove).add(req));

    Set<WatchRequest> result = new HashSet<>(recursiveRootsToAdd.size() + flatRootsToAdd.size());

    synchronized (myLock) {
      updateWatchRoots(recursiveRootsToAdd, recursiveRequestsToRemove, result, myRecursiveWatchRoots, true);
      updateWatchRoots(flatRootsToAdd, flatRequestsToRemove, result, myFlatWatchRoots, false);
      if (myWatcherRequiresUpdate) {
        updateFileWatcher();
      }
    }

    return result;
  }

  void clear() {
    synchronized (myLock) {
      myRecursiveWatchRoots.clear();
      myOptimizedRecursiveWatchRoots.clear();
      myFlatWatchRoots.clear();
      myPathMappings.clear();

      mySymlinksByPath.clear();
      mySymlinksById.values().forEach(SymlinkData::clear);
      mySymlinksById.clear();
    }
  }

  void updateSymlink(int fileId, @NotNull String linkPath, @Nullable String linkTarget) {
    synchronized (myLock) {
      SymlinkData data = mySymlinksById.get(fileId);
      if (data != null) {
        if (FileUtil.pathsEqual(data.path, linkPath) && FileUtil.pathsEqual(data.target, linkTarget)) {
          // avoiding costly removal and re-addition of the request in case of a no-op update
          return;
        }
        mySymlinksById.remove(fileId);
        mySymlinksByPath.remove(data.path);
        data.removeRequest(this);
      }

      data = new SymlinkData(fileId, linkPath, linkTarget);

      SymlinkData existing = mySymlinksByPath.get(linkPath);
      if (existing != null) {
        LOG.error("Path conflict. Existing symlink: " + existing + " vs. incoming symlink: " + data);
        return;
      }

      mySymlinksByPath.put(data.path, data);
      mySymlinksById.put(data.id, data);
      if (data.hasValidTarget() && WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, data.path)) {
        addWatchSymlinkRequest(data.getWatchRequest());
      }
    }
  }

  void removeSymlink(int fileId) {
    synchronized (myLock) {
      SymlinkData data = mySymlinksById.remove(fileId);
      if (data != null) {
        mySymlinksByPath.remove(data.path);
        data.removeRequest(this);
      }
    }
  }

  private void updateFileWatcher() {
    myFileWatcher.setWatchRoots(() -> {
      synchronized (myLock) {
        if (!myWatcherRequiresUpdate) return null;
        myWatcherRequiresUpdate = false;
        var convert = File.separatorChar == '\\';
        return createCanonicalPathMap(myFlatWatchRoots.navigableKeySet(), myOptimizedRecursiveWatchRoots, myPathMappings, convert);
      }
    });
  }

  static @NotNull CanonicalPathMap createCanonicalPathMap(
    @NotNull Set<String> flatWatchRoots,
    @NotNull Set<String> optimizedRecursiveWatchRoots,
    @NotNull Collection<Pair<String, String>> pathMappings,
    boolean convertToForwardSlashes
  ) {
    NavigableSet<@SystemDependent String> optimizedRecursiveWatchRootsCopy = WatchRootsUtil.createFileNavigableSet();
    List<Pair<@SystemDependent String, @SystemDependent String>> initialMappings = new ArrayList<>(pathMappings.size());

    // Ensure paths are system dependent
    if (!convertToForwardSlashes) {
      optimizedRecursiveWatchRootsCopy.addAll(optimizedRecursiveWatchRoots);
      initialMappings.addAll(pathMappings);
    }
    else {
      for (String recursiveWatchRoot : optimizedRecursiveWatchRoots) {
        optimizedRecursiveWatchRootsCopy.add(recursiveWatchRoot.replace('/', '\\'));
      }
      for (Pair<String, String> mapping : pathMappings) {
        initialMappings.add(new Pair<>(mapping.first.replace('/', '\\'),
                                       mapping.second.replace('/', '\\')));
      }
    }
    NavigableSet<@SystemDependent String> optimizedFlatWatchRoots =
      WatchRootsUtil.optimizeFlatRoots(flatWatchRoots, optimizedRecursiveWatchRootsCopy, convertToForwardSlashes);
    return new CanonicalPathMap(optimizedRecursiveWatchRootsCopy, optimizedFlatWatchRoots, initialMappings);
  }

  private void updateWatchRoots(
    Collection<String> rootsToAdd, Set<WatchRequest> requestsToRemove,
    Set<WatchRequest> result,
    Map<String, List<WatchRequest>> roots,
    boolean recursiveWatchRoots
  ) {
    List<WatchSymlinkRequest> watchSymlinkRequestsToAdd = new SmartList<>();
    for (String root : rootsToAdd) {
      String watchRoot = prepareWatchRoot(root);
      if (watchRoot == null) continue;

      List<WatchRequest> requests = roots.computeIfAbsent(watchRoot, __ -> new SmartList<>());
      boolean foundSameRequest = false;
      if (!requestsToRemove.isEmpty()) {
        for (WatchRequest currentRequest : requests) {
          if (requestsToRemove.remove(currentRequest)) {
            foundSameRequest = true;
            result.add(currentRequest);
          }
        }
      }

      if (!foundSameRequest) {
        WatchRequestImpl newRequest = new WatchRequestImpl(watchRoot, recursiveWatchRoots);
        requests.add(newRequest);
        result.add(newRequest);
        if (recursiveWatchRoots) {
          collectSymlinkRequests(newRequest, watchSymlinkRequestsToAdd);
        }
        if (requests.size() == 1 && !WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, watchRoot)) {
          myWatcherRequiresUpdate = true;
          if (recursiveWatchRoots) {
            WatchRootsUtil.insertRecursivePath(myOptimizedRecursiveWatchRoots, watchRoot);
          }
        }
      }
    }

    List<WatchSymlinkRequest> watchSymlinkRequestsToRemove = new SmartList<>();
    for (WatchRequest request : requestsToRemove) {
      removeWatchRequest(request);
      if (recursiveWatchRoots) {
        collectSymlinkRequests((WatchRequestImpl)request, watchSymlinkRequestsToRemove);
      }
    }

    if (recursiveWatchRoots) {
      addWatchSymlinkRequests(watchSymlinkRequestsToAdd);
      removeWatchSymlinkRequests(watchSymlinkRequestsToRemove);
    }
  }

  private static @Nullable String prepareWatchRoot(String root) {
    int index = root.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (index >= 0) root = root.substring(0, index);
    try {
      Path rootPath = Path.of(FileUtil.toSystemDependentName(root));
      if (!rootPath.isAbsolute()) throw new InvalidPathException(root, "Watch roots should be absolute");
      checkRootIsSane(rootPath);
      return FileUtil.toSystemIndependentName(rootPath.toString());
    }
    catch (InvalidPathException e) {
      LOG.warn("invalid watch root", e);
      return null;
    }
  }

  private static void checkRootIsSane(Path rootPath) {
    if (rootPath.startsWith("/proc")) {
      LOG.error("One shouldn't use [" + rootPath + "] as watch root");
    }
  }

  private void removeWatchRequest(WatchRequest request) {
    String watchRoot = request.getRootPath();
    Map<String, List<WatchRequest>> roots = request.isToWatchRecursively() ? myRecursiveWatchRoots : myFlatWatchRoots;
    List<WatchRequest> requests = roots.get(watchRoot);
    if (requests != null) {
      requests.remove(request);
      if (requests.isEmpty()) {
        roots.remove(watchRoot);
        if (request.isToWatchRecursively()) {
          if (WatchRootsUtil.removeRecursivePath(myOptimizedRecursiveWatchRoots, myRecursiveWatchRoots, watchRoot)) {
            myWatcherRequiresUpdate = true;
          }
        }
        else if (!WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, watchRoot)) {
          myWatcherRequiresUpdate = true;
        }
      }
    }
  }

  private void addWatchSymlinkRequests(List<WatchSymlinkRequest> watchSymlinkRequestsToAdd) {
    for (WatchSymlinkRequest request : watchSymlinkRequestsToAdd) {
      if (!request.getRootPath().isEmpty() && !request.isRegistered()) {
        addWatchSymlinkRequest(request);
      }
    }
  }

  private void addWatchSymlinkRequest(WatchSymlinkRequest request) {
    String watchRoot = request.getRootPath();
    Map<String, List<WatchRequest>> roots = request.isToWatchRecursively() ? myRecursiveWatchRoots : myFlatWatchRoots;
    List<WatchRequest> requests = roots.computeIfAbsent(watchRoot, __ -> new SmartList<>());
    requests.add(request);
    if (requests.size() == 1 && !WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, watchRoot)) {
      if (request.isToWatchRecursively()) {
        WatchRootsUtil.insertRecursivePath(myOptimizedRecursiveWatchRoots, watchRoot);
      }
    }
    if (request.setRegistered(true)) {
      myWatcherRequiresUpdate = true;
      myPathMappings.add(new Pair<>(watchRoot, request.getOriginalPath()));
    }
  }

  private void removeWatchSymlinkRequests(List<WatchSymlinkRequest> watchSymlinkRequestsToRemove) {
    for (WatchSymlinkRequest request : watchSymlinkRequestsToRemove) {
      Ref<Boolean> remove = new Ref<>(true);
      WatchRootsUtil.forEachPathSegment(request.getOriginalPath(), '/', path -> {
        List<WatchRequest> requests = myRecursiveWatchRoots.get(path);
        if (requests != null && ContainerUtil.findInstance(requests, WatchRequestImpl.class) != null) {
          remove.set(false);
          return false;
        }
        return true;
      });
      if (remove.get()) {
        removeWatchSymlinkRequest(request);
      }
    }
  }

  private void removeWatchSymlinkRequest(WatchSymlinkRequest request) {
    if (!request.isRegistered()) {
      return;
    }
    removeWatchRequest(request);
    if (request.setRegistered(false)) {
      myPathMappings.remove(new Pair<>(request.getRootPath(), request.getOriginalPath()));
      myWatcherRequiresUpdate = true;
    }
  }

  private void collectSymlinkRequests(WatchRequestImpl newRequest, /*OutParam*/ Collection<WatchSymlinkRequest> watchSymlinkRequestsToAdd) {
    assert newRequest.isToWatchRecursively() : newRequest;
    WatchRootsUtil.collectByPrefix(mySymlinksByPath, newRequest.getRootPath(), e -> {
      if (e.getValue().hasValidTarget()) {
        watchSymlinkRequestsToAdd.add(e.getValue().getWatchRequest());
      }
    });
  }

  private static final class WatchRequestImpl implements WatchRequest {
    private final String myFSRootPath;
    private final boolean myWatchRecursively;

    private WatchRequestImpl(@NotNull String rootPath, boolean watchRecursively) {
      myFSRootPath = rootPath;
      myWatchRecursively = watchRecursively;
    }

    @Override
    public @NotNull @SystemIndependent String getRootPath() {
      return myFSRootPath;
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

  private static final class WatchSymlinkRequest implements WatchRequest {
    private final SymlinkData mySymlinkData;
    private final boolean myWatchRecursively;
    private boolean myRegistered = false;

    private WatchSymlinkRequest(SymlinkData data, boolean watchRecursively) {
      mySymlinkData = data;
      assert mySymlinkData.hasValidTarget();
      myWatchRecursively = watchRecursively;
    }

    private boolean isRegistered() {
      return myRegistered;
    }

    private boolean setRegistered(boolean registered) {
      if (myRegistered != registered) {
        myRegistered = registered;
        return true;
      }
      return false;
    }

    @Override
    public @NotNull @SystemIndependent String getRootPath() {
      return Objects.requireNonNull(mySymlinkData.target);
    }

    @Override
    public boolean isToWatchRecursively() {
      return myWatchRecursively;
    }

    String getOriginalPath() {
      return mySymlinkData.path;
    }
  }

  private static final class SymlinkData {
    private final int id;
    private final @SystemIndependent String path;
    private final @Nullable @SystemIndependent String target;
    private WatchSymlinkRequest myWatchRequest;

    private SymlinkData(int id, String path, @Nullable String target) {
      this.id = id;
      this.path = FileUtil.toSystemIndependentName(path);
      this.target = target != null ? FileUtil.toSystemIndependentName(target) : null;
    }

    private WatchSymlinkRequest getWatchRequest() {
      assert hasValidTarget();
      if (myWatchRequest == null) {
        myWatchRequest = new WatchSymlinkRequest(this, true);
      }
      return myWatchRequest;
    }

    private boolean hasValidTarget() {
      return target != null;
    }

    private void removeRequest(WatchRootsManager manager) {
      if (myWatchRequest != null) {
        manager.removeWatchSymlinkRequest(myWatchRequest);
        myWatchRequest = null;
      }
    }

    private void clear() {
      myWatchRequest = null;
    }

    @Override
    public String toString() {
      return "SymlinkData{" + id + ", " + path + " -> " + target + "}[" + (myWatchRequest == null ? "cleared" : "valid") + "]";
    }
  }
}
