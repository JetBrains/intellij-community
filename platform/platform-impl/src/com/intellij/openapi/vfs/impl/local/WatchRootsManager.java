// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem.WatchRequest;
import com.intellij.openapi.vfs.newvfs.persistent.SymlinkRegistry;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemDependent;
import org.jetbrains.annotations.SystemIndependent;

import java.io.File;
import java.util.*;

public class WatchRootsManager {

  protected static final Logger LOG = Logger.getInstance(WatchRootsManager.class);

  private final FileWatcher myFileWatcher;
  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private boolean watcherRequiresUpdate; // Synchronized on myLock

  private final Object myLock = new Object();

  private final NavigableMap<String, List<WatchRequest>> myRecursiveWatchRoots = WatchRootsUtil.createFileNavigableMap();
  private final NavigableMap<String, List<WatchRequest>> myFlatWatchRoots = WatchRootsUtil.createFileNavigableMap();

  private final NavigableSet<String> myOptimizedRecursiveWatchRoots = WatchRootsUtil.createFileNavigableSet();

  private final NavigableMap<String, SymlinkData> mySymlinksByPath = WatchRootsUtil.createFileNavigableMap();
  private final TIntObjectHashMap<SymlinkData> mySymlinksById = new TIntObjectHashMap<>();
  private final MultiMap<String, String> myPathMappings = MultiMap.createConcurrentSet();


  public WatchRootsManager(@NotNull FileWatcher fileWatcher,
                           @NotNull Disposable parentDisposable) {
    myFileWatcher = fileWatcher;
    SymlinkRegistry.INSTANCE.watchSymlinks(this::processSymlinkEvents, parentDisposable);
  }

  @NotNull
  public Set<WatchRequest> replaceWatchedRoots(@NotNull Collection<WatchRequest> watchRequestsToRemove,
                                               @NotNull Collection<String> recursiveRootsToAdd,
                                               @NotNull Collection<String> flatRootsToAdd) {
    Set<WatchRequest> result = new HashSet<>(recursiveRootsToAdd.size() + flatRootsToAdd.size());

    synchronized (myLock) {
      watcherRequiresUpdate = false;

      updateWatchRoots(recursiveRootsToAdd,
                       ContainerUtil.map2SetNotNull(watchRequestsToRemove, req -> req.isToWatchRecursively() ? req : null),
                       result, myRecursiveWatchRoots, true);

      updateWatchRoots(flatRootsToAdd,
                       ContainerUtil.map2SetNotNull(watchRequestsToRemove, req -> req.isToWatchRecursively() ? null : req),
                       result, myFlatWatchRoots, false);

      if (watcherRequiresUpdate) {
        updateFileWatcher();
      }
    }
    return result;
  }

  public void clear() {
    synchronized (myLock) {
      myRecursiveWatchRoots.clear();
      myOptimizedRecursiveWatchRoots.clear();
      myFlatWatchRoots.clear();
      myPathMappings.clear();
      mySymlinksById.forEachValue(data -> { data.clear(); return true; });
    }
  }

  private void updateFileWatcher() {
    Iterable<@SystemDependent String> flatWatchRootsIterable;
    NavigableSet<@SystemDependent String> recursiveWatchRoots = WatchRootsUtil.createFileNavigableSet();
    MultiMap<@SystemDependent String, @SystemDependent String> initialMappings = MultiMap.create();

    // Ensure paths are system dependent
    if (File.separatorChar == '/') {
      flatWatchRootsIterable = myFlatWatchRoots.navigableKeySet();
      recursiveWatchRoots.addAll(myOptimizedRecursiveWatchRoots);
      initialMappings.putAllValues(myPathMappings);
    } else {
      Function<String, String> pathMapper = path -> path.replace('/', File.separatorChar);
      flatWatchRootsIterable = JBIterable.from(myFlatWatchRoots.navigableKeySet()).map(pathMapper);
      JBIterable.from(myOptimizedRecursiveWatchRoots).map(pathMapper).addAllTo(recursiveWatchRoots);
      for (Map.Entry<String, Collection<String>> entry: myPathMappings.entrySet()) {
        initialMappings.putValues(pathMapper.fun(entry.getKey()), ContainerUtil.map(entry.getValue(), pathMapper));
      }
    }
    NavigableSet<@SystemDependent String> flatWatchRoots = WatchRootsUtil.optimizeFlatRoots(flatWatchRootsIterable, recursiveWatchRoots);
    myFileWatcher.setWatchRoots(new CanonicalPathMap(recursiveWatchRoots, flatWatchRoots, initialMappings));
  }

  private void updateWatchRoots(@NotNull Collection<String> rootsToAdd,
                                Set<WatchRequest> toRemoveSet,
                                Set<WatchRequest> result,
                                NavigableMap<String, List<WatchRequest>> roots,
                                boolean recursiveWatchRoots) {
    List<WatchSymlinkRequest> watchSymlinkRequestsToAdd = new SmartList<>();
    for (String watchRoot : rootsToAdd) {
      watchRoot = WatchRootsUtil.mapToSystemPath(watchRoot);
      if (watchRoot != null) {
        List<WatchRequest> requests = roots.computeIfAbsent(watchRoot, (key) -> new SmartList<>());
        boolean foundSameRequest = false;
        if (!toRemoveSet.isEmpty()) {
          for (WatchRequest currentRequest : requests) {
            if (toRemoveSet.remove(currentRequest)) {
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
            watcherRequiresUpdate = true;
            if (recursiveWatchRoots) {
              WatchRootsUtil.insertRecursivePath(myOptimizedRecursiveWatchRoots, watchRoot);
            }
          }
        }
      }
    }

    List<WatchSymlinkRequest> watchSymlinkRequestsToRemove = new SmartList<>();
    for (WatchRequest request : toRemoveSet) {
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

  private void removeWatchRequest(WatchRequest request) {
    String watchRoot = request.getRootPath();
    NavigableMap<String, List<WatchRequest>> roots = request.isToWatchRecursively()
                                                     ? myRecursiveWatchRoots
                                                     : myFlatWatchRoots;
    List<WatchRequest> requests = roots.get(watchRoot);
    if (requests != null) {
      requests.remove(request);
      if (requests.isEmpty()) {
        roots.remove(watchRoot);
        if (request.isToWatchRecursively()) {
          if (WatchRootsUtil.removeRecursivePath(myOptimizedRecursiveWatchRoots, myRecursiveWatchRoots, watchRoot)) {
            watcherRequiresUpdate = true;
          }
        }
        else if (!WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, watchRoot)) {
          watcherRequiresUpdate = true;
        }
      }
    }
  }

  private void processSymlinkEvents(List<SymlinkRegistry.SymlinkEvent> events) {
    synchronized (myLock) {
      watcherRequiresUpdate = false;
      for (SymlinkRegistry.SymlinkEvent event : events) {
        switch (event.eventType) {
          case ADDED:
            addSymlink(event);
            break;
          case DELETED:
            removeSymlink(event.fileId);
            break;
          case UPDATED:
            removeSymlink(event.fileId);
            addSymlink(event);
            break;
        }
      }
      if (watcherRequiresUpdate) {
        updateFileWatcher();
      }
    }
  }

  private void addSymlink(SymlinkRegistry.SymlinkEvent event) {
    SymlinkData existing = mySymlinksByPath.get(event.linkPath);
    if (existing != null) {
      if (existing.id == event.fileId) {
        LOG.warn("Duplicated add symlink event on: " + existing);
      }
      else {
        LOG.error("Path conflict. Existing symlink: " + existing + " vs. new symlink: " + event);
      }
      return;
    }

    SymlinkData data = new SymlinkData(event.fileId, event.linkPath, event.linkTarget);
    mySymlinksByPath.put(data.path, data);
    mySymlinksById.put(data.id, data);
    if (WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, data.path)) {
      addWatchSymlinkRequest(data.getWatchRequest());
    }
  }

  private void removeSymlink(int fileId) {
    SymlinkData data = mySymlinksById.remove(fileId);
    if (data != null && data.path != null) {
      data.removeRequest(this);
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
    NavigableMap<String, List<WatchRequest>> roots = request.isToWatchRecursively()
                                                     ? myRecursiveWatchRoots
                                                     : myFlatWatchRoots;
    String watchRoot = request.getRootPath();
    List<WatchRequest> requests = roots.computeIfAbsent(watchRoot, (key) -> new SmartList<>());
    requests.add(request);
    if (requests.size() == 1 && !WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, watchRoot)) {
      if (request.isToWatchRecursively()) {
        WatchRootsUtil.insertRecursivePath(myOptimizedRecursiveWatchRoots, watchRoot);
      }
    }
    if (request.setRegistered(true)) {
      watcherRequiresUpdate = true;
      myPathMappings.putValue(watchRoot, request.getOriginalPath());
    }
  }

  private void removeWatchSymlinkRequests(List<WatchSymlinkRequest> watchSymlinkRequestsToRemove) {
    for (WatchSymlinkRequest request : watchSymlinkRequestsToRemove) {
      Ref<Boolean> remove = new Ref<>(true);
      WatchRootsUtil.forEachFilePathSegment(request.getOriginalPath(), '/', subPath -> {
        List<WatchRequest> requests = myRecursiveWatchRoots.get(subPath);
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
      myPathMappings.remove(request.getRootPath(), request.getOriginalPath());
      watcherRequiresUpdate = true;
    }
  }

  private void collectSymlinkRequests(WatchRequestImpl newRequest,
                                      Collection<WatchSymlinkRequest> watchSymlinkRequestsToAdd) {
    assert newRequest.isToWatchRecursively() : newRequest;
    WatchRootsUtil.collectByPrefix(mySymlinksByPath, newRequest.myFSRootPath, entry -> watchSymlinkRequestsToAdd.add(
      entry.getValue().getWatchRequest()));
  }

  private static class WatchRequestImpl implements WatchRequest {
    private final String myFSRootPath;
    private final boolean myWatchRecursively;

    private WatchRequestImpl(@SystemIndependent @NotNull String rootPath, boolean watchRecursively) {
      myFSRootPath = rootPath;
      myWatchRecursively = watchRecursively;
    }

    @Override
    @NotNull
    @SystemIndependent
    public String getRootPath() {
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

  private static class WatchSymlinkRequest implements WatchRequest {
    private final SymlinkData mySymlinkData;
    private final boolean myWatchRecursively;
    private boolean registered = false;

    WatchSymlinkRequest(SymlinkData data, boolean watchRecursively) {
      mySymlinkData = data;
      myWatchRecursively = watchRecursively;
    }

    boolean isRegistered() {
      return registered;
    }

    boolean setRegistered(boolean registered) {
      if (this.registered != registered) {
        this.registered = registered;
        return true;
      }
      return false;
    }

    @Override
    public @NotNull @SystemIndependent String getRootPath() {
      return mySymlinkData.target;
    }

    @Override
    public boolean isToWatchRecursively() {
      return myWatchRecursively;
    }

    String getOriginalPath() {
      return mySymlinkData.path;
    }
  }

  private static class SymlinkData {
    final int id;
    final String path;
    final String target;
    private WatchSymlinkRequest myWatchRequest;

    SymlinkData(int id, String path, @Nullable String target) {
      this.id = id;
      this.path = WatchRootsUtil.normalizeFileName(path);
      this.target = StringUtil.notNullize(WatchRootsUtil.normalizeFileName(target));
    }

    @NotNull WatchSymlinkRequest getWatchRequest() {
      if (myWatchRequest == null) {
        myWatchRequest = new WatchSymlinkRequest(this, true);
      }
      return myWatchRequest;
    }

    void removeRequest(WatchRootsManager manager) {
      if (myWatchRequest != null) {
        manager.removeWatchSymlinkRequest(myWatchRequest);
        myWatchRequest = null;
      }
    }

    void clear() {
      myWatchRequest = null;
    }

    @Override
    public String toString() {
      return "SymlinkData{" + id + ", " + path + " -> " + target + '}';
    }
  }
}