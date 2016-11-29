/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

public class RemoteRevisionsStateCache implements ChangesOnServerTracker {
  private final static long DISCRETE = 3600000;
  // All files that were checked during cache update and were not invalidated.
  // pair.First - if file is changed (true means changed)
  // pair.Second - vcs root where file belongs to
  private final Map<String, Pair<Boolean, VcsRoot>> myChanged;

  // All files that needs to be checked during next cache update, grouped by vcs root
  private final MultiMap<VcsRoot, String> myQueries;
  // All vcs roots for which cache update was performed with update timestamp
  private final Map<VcsRoot, Long> myTs;
  private final Object myLock;
  private final ProjectLevelVcsManager myVcsManager;
  private final VcsConfiguration myVcsConfiguration;

  RemoteRevisionsStateCache(final Project project) {
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myChanged = new HashMap<>();
    myQueries = new MultiMap<>();
    myTs = new HashMap<>();
    myLock = new Object();
    myVcsConfiguration = VcsConfiguration.getInstance(project);
  }

  public void invalidate(final Collection<String> paths) {
    synchronized (myLock) {
      for (String path : paths) {
        myChanged.remove(path);
      }
    }
  }

  @Nullable
  private VirtualFile getRootForPath(final String s) {
    return myVcsManager.getVcsRootFor(VcsUtil.getFilePath(s, false));
  }
  
  public boolean isUpToDate(final Change change) {
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

  public void plus(final Pair<String, AbstractVcs> pair) {
    final VirtualFile root = getRootForPath(pair.getFirst());
    if (root == null) return;
    synchronized (myLock) {
      myQueries.putValue(new VcsRoot(pair.getSecond(), root), pair.getFirst());
    }
  }

  public void minus(Pair<String, AbstractVcs> pair) {
    final VirtualFile root = getRootForPath(pair.getFirst());
    if (root == null) return;
    synchronized (myLock) {
      final VcsRoot key = new VcsRoot(pair.getSecond(), root);
      if (myQueries.containsKey(key)) {
        myQueries.remove(key, pair.getFirst());
      }
      myChanged.remove(pair.getFirst());
    }
  }

  public void directoryMappingChanged() {
    // todo will work?
    synchronized (myLock) {
      myChanged.clear();
      myTs.clear();
    }
  }

  public boolean updateStep() {
    final MultiMap<VcsRoot, String> dirty = new MultiMap<>();
    final long oldPoint = System.currentTimeMillis() - (myVcsConfiguration.CHANGED_ON_SERVER_INTERVAL > 0 ?
                                                        myVcsConfiguration.CHANGED_ON_SERVER_INTERVAL * 60000 : DISCRETE);

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
}
