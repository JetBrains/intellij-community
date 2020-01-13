// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.LocalFileSystem.WatchRequest;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.openapi.vfs.newvfs.persistent.SymlinkRegistry;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.containers.MultiMap;
import one.util.streamex.StreamEx;
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
  private final Map<Integer, SymlinkData> mySymlinksById = new HashMap<>();
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
      mySymlinksById.values().forEach(data -> data.clear());
    }
  }

  private void updateFileWatcher() {
    Iterable<@SystemDependent String> flatWatchRootsIterable;
    NavigableSet<@SystemDependent String> recursiveWatchRoots = WatchRootsUtil.createFileNavigableSet();
    MultiMap<@SystemDependent String, @SystemDependent String> initialMappings = MultiMap.createConcurrentSet();

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
    myFileWatcher.setWatchRoots(recursiveWatchRoots, flatWatchRoots, initialMappings);
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
            addSymlink(event.fileId);
            break;
          case DELETED:
            removeSymlink(event.fileId);
            break;
          case UPDATED:
            removeSymlink(event.fileId);
            addSymlink(event.fileId);
            break;
        }
      }
      if (watcherRequiresUpdate) {
        updateFileWatcher();
      }
    }
  }

  private void addSymlink(int fileId) {
    SymlinkData data = new SymlinkData(fileId);
    if (data.path != null) {
      SymlinkData existing = mySymlinksByPath.get(data.path);
      if (existing != null) {
        if (existing.id == fileId) {
          LOG.warn("Duplicated add symlink event on: " + existing);
        }
        else {
          LOG.error("Path conflict. Existing symlink: " + existing + " vs. new symlink: " + data);
        }
        return;
      }
      mySymlinksByPath.put(data.path, data);
      mySymlinksById.put(fileId, data);
      if (WatchRootsUtil.isCoveredRecursively(myOptimizedRecursiveWatchRoots, data.path)) {
        addWatchSymlinkRequest(data.getWatchRequest());
      }
    }
  }

  private void removeSymlink(int fileId) {
    SymlinkData data = mySymlinksById.remove(fileId);
    if (data != null && data.path != null) {
      data.removeRequest(this);
    }
  }

  private void addWatchSymlinkRequests(List<WatchSymlinkRequest> watchSymlinkRequestsToAdd) {
    if (watchSymlinkRequestsToAdd.size() > 5) {
      StreamEx.of(watchSymlinkRequestsToAdd)
        .parallel()
        .forEach(WatchSymlinkRequest::initialize);
    }
    else {
      watchSymlinkRequestsToAdd.forEach(WatchSymlinkRequest::initialize);
    }

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
    private String mySymlinkTarget;
    private final boolean myWatchRecursively;
    private boolean registered = false;

    private WatchSymlinkRequest(SymlinkData data, boolean watchRecursively) {
      mySymlinkData = data;
      myWatchRecursively = watchRecursively;
    }

    public void initialize() {
      if (mySymlinkTarget == null) {
        mySymlinkTarget = mySymlinkData.getSymlinkTarget();
        if (mySymlinkTarget == null) {
          mySymlinkTarget = "";
        }
      }
    }

    private boolean isRegistered() {
      return registered;
    }

    private boolean setRegistered(boolean registered) {
      if (this.registered != registered) {
        this.registered = registered;
        return true;
      }
      return false;
    }

    @NotNull
    @Override
    public @SystemIndependent String getRootPath() {
      return mySymlinkTarget;
    }

    @Override
    public boolean isToWatchRecursively() {
      return myWatchRecursively;
    }

    public String getOriginalPath() {
      return mySymlinkData.path;
    }
  }

  private static class SymlinkData {

    public final int id;
    public final String path;

    private WatchSymlinkRequest myRecursiveRequest;

    private SymlinkData(int id) {
      this.id = id;
      VirtualFile vf = PersistentFS.getInstance().findFileById(id);
      if (vf == null) {
        // TODO figure out how to fix race condition resulting in null here
        LOG.warn("SymlinkData: cannot find virtual file with id " + id);
      }
      this.path = vf != null ? WatchRootsUtil.normalizeFileName(vf.getPath())
                             : null;
    }

    @Nullable
    public String getSymlinkTarget() {
      VirtualFile vf = PersistentFS.getInstance().findFileById(id);
      return vf != null ? WatchRootsUtil.normalizeFileName(PersistentFS.getInstance().resolveSymLink(vf))
                        : null;
    }

    @NotNull
    public WatchSymlinkRequest getWatchRequest() {
      if (myRecursiveRequest == null) {
        myRecursiveRequest = new WatchSymlinkRequest(this, true);
      }
      return myRecursiveRequest;
    }

    public void removeRequest(WatchRootsManager manager) {
      if (myRecursiveRequest != null) {
        manager.removeWatchSymlinkRequest(myRecursiveRequest);
        myRecursiveRequest = null;
      }
    }

    private void clear() {
      myRecursiveRequest = null;
    }

    @Override
    public String toString() {
      return "SymlinkData{" +
             "id=" + id +
             ", path='" + path + '"' +
             '}';
    }
  }
}
