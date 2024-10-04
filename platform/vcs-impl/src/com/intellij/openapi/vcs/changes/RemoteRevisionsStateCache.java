// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.TreeDiffProvider;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.intellij.vcsUtil.VcsUtil.getFilePath;
import static com.intellij.vcsUtil.VcsUtil.getVcsRootFor;

@ApiStatus.Internal
public class RemoteRevisionsStateCache implements ChangesOnServerTracker {
  private final static long DISCRETE = 3600000;
  // All files that were checked during cache update and were not invalidated.
  // pair.First - if file is changed (true means changed)
  // pair.Second - vcs root where file belongs to
  private final @NotNull Map<String, Pair<Boolean, VcsRoot>> myChanged = new HashMap<>();

  // All files that needs to be checked during next cache update, grouped by vcs root
  private final @NotNull MultiMap<VcsRoot, String> myQueries = new MultiMap<>();
  // All vcs roots for which cache update was performed with update timestamp
  private final @NotNull Map<VcsRoot, Long> myTs = new HashMap<>();
  private final @NotNull Object myLock = new Object();
  private final @NotNull Project myProject;

  RemoteRevisionsStateCache(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public void invalidate(final Collection<String> paths) {
    synchronized (myLock) {
      for (String path : paths) {
        myChanged.remove(path);
      }
    }
  }

  @Override
  public boolean isUpToDate(@NotNull Change change, @NotNull AbstractVcs vcs) {
    if (!isSupportedFor(vcs)) return true;

    final List<File> files = ChangesUtil.getIoFilesFromChanges(Collections.singletonList(change));
    synchronized (myLock) {
      for (File file : files) {
        final String path = file.getAbsolutePath();
        final Pair<Boolean, VcsRoot> data = myChanged.get(path);
        if (data != null && Boolean.TRUE.equals(data.getFirst())) return false;
      }
    }
    return true;
  }

  @Override
  public void changeUpdated(@NotNull String path, @NotNull AbstractVcs vcs) {
    if (!isSupportedFor(vcs)) return;

    final VirtualFile root = getVcsRootFor(myProject, getFilePath(path, false));
    if (root == null) return;
    synchronized (myLock) {
      myQueries.putValue(new VcsRoot(vcs, root), path);
    }
  }

  @Override
  public void changeRemoved(@NotNull String path, @NotNull AbstractVcs vcs) {
    if (!isSupportedFor(vcs)) return;

    final VirtualFile root = getVcsRootFor(myProject, getFilePath(path, false));
    if (root == null) return;
    synchronized (myLock) {
      final VcsRoot key = new VcsRoot(vcs, root);
      if (myQueries.containsKey(key)) {
        myQueries.remove(key, path);
      }
      myChanged.remove(path);
    }
  }

  @Override
  public void directoryMappingChanged() {
    // todo will work?
    synchronized (myLock) {
      myChanged.clear();
      myTs.clear();
    }
  }

  @Override
  public boolean updateStep() {
    final MultiMap<VcsRoot, String> dirty = new MultiMap<>();
    int interval = VcsConfiguration.getInstance(myProject).CHANGED_ON_SERVER_INTERVAL;
    final long oldPoint = System.currentTimeMillis() - (interval > 0 ? interval * 60000L : DISCRETE);

    synchronized (myLock) {
      // just copies myQueries MultiMap to dirty MultiMap
      for (VcsRoot root : myQueries.keySet()) {
        final Collection<String> collection = myQueries.get(root);
        for (String s : collection) {
          dirty.putValue(root, s);
        }
      }
      myQueries.clear();

      // collect roots for which cache update should be performed (by timestamp)
      final Set<VcsRoot> roots = new HashSet<>();
      for (Map.Entry<VcsRoot, Long> entry : myTs.entrySet()) {
        // ignore timestamp, as still remote changes checking is required
        // TODO: why not to add in roots anyway??? - as dirty is still checked when adding myChanged files.
        if (! dirty.get(entry.getKey()).isEmpty()) continue;

        // update only if timeout expired
        final Long ts = entry.getValue();
        if ((ts == null) || (oldPoint > ts)) {
          roots.add(entry.getKey());
        }
      }

      // Add dirty files from those vcs roots, that
      // - needs to be update by timestamp criteria
      // - that already contain files for update through manually added requests
      for (Map.Entry<String, Pair<Boolean, VcsRoot>> entry : myChanged.entrySet()) {
        final VcsRoot vcsRoot = entry.getValue().getSecond();
        if ((! dirty.get(vcsRoot).isEmpty()) || roots.contains(vcsRoot)) {
          dirty.putValue(vcsRoot, entry.getKey());
        }
      }
    }

    if (dirty.isEmpty()) return false;

    final Map<String, Pair<Boolean, VcsRoot>> results = new HashMap<>();
    for (VcsRoot vcsRoot : dirty.keySet()) {
      // todo - actually it means nothing since the only known VCS to use this scheme is Git and now it always allow
      // todo - background operations. when it changes, develop more flexible behavior here
      if (! vcsRoot.getVcs().isVcsBackgroundOperationsAllowed(vcsRoot.getPath())) continue;
      final TreeDiffProvider provider = vcsRoot.getVcs().getTreeDiffProvider();
      if (provider == null) continue;

      final Collection<String> paths = dirty.get(vcsRoot);
      final Collection<String> remotelyChanged = provider.getRemotelyChanged(vcsRoot.getPath(), paths);
      for (String path : paths) {
        // TODO: Contains invoked for each file - better to use Set (implementations just use List)
        // TODO: Why to store boolean for changed or not - why not just remove such values from myChanged???
        results.put(path, new Pair<>(remotelyChanged.contains(path), vcsRoot));
      }
    }

    final long curTime = System.currentTimeMillis();
    synchronized (myLock) {
      myChanged.putAll(results);
      for (VcsRoot vcsRoot : dirty.keySet()) {
        myTs.put(vcsRoot, curTime);
      }
    }

    return true;
  }

  private static boolean isSupportedFor(@NotNull AbstractVcs vcs) {
    return vcs.getTreeDiffProvider() != null;
  }
}
