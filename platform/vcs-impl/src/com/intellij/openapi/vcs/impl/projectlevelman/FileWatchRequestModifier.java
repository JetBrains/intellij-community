// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FileWatchRequestModifier implements Runnable {
  private static final Logger LOG = Logger.getInstance(FileWatchRequestModifier.class);

  private final Project myProject;
  private final NewMappings myNewMappings;
  private final Map<VcsDirectoryMapping, LocalFileSystem.WatchRequest> myDirectoryMappingWatches;
  private final LocalFileSystem myLfs;

  public FileWatchRequestModifier(@NotNull Project project, @NotNull NewMappings newMappings, @NotNull LocalFileSystem localFileSystem) {
    myLfs = localFileSystem;
    myProject = project;
    myNewMappings = newMappings;
    myDirectoryMappingWatches = new THashMap<>();
  }

  @Override
  public void run() {
    if (!myProject.isInitialized() || myProject.isDisposed()) return;
    final List<VcsDirectoryMapping> copy = myNewMappings.getDirectoryMappings();

    final List<VcsDirectoryMapping> added = new ArrayList<>(copy);
    added.removeAll(myDirectoryMappingWatches.keySet());

    final List<VcsDirectoryMapping> deleted = new ArrayList<>(myDirectoryMappingWatches.keySet());
    deleted.removeAll(copy);

    final Map<String, VcsDirectoryMapping> toAdd = new THashMap<>(FileUtil.PATH_HASHING_STRATEGY);
    for (VcsDirectoryMapping mapping : added) {
      if (!mapping.isDefaultMapping()) {
        toAdd.put(FileUtil.toCanonicalPath(mapping.getDirectory()), mapping);
      }
    }

    final Collection<LocalFileSystem.WatchRequest> toRemove = new ArrayList<>();
    for (VcsDirectoryMapping mapping : deleted) {
      if (mapping.isDefaultMapping()) continue;
      final LocalFileSystem.WatchRequest removed = myDirectoryMappingWatches.remove(mapping);
      if (removed != null) {
        toRemove.add(removed);
      }
    }

    final Set<LocalFileSystem.WatchRequest> requests = myLfs.replaceWatchedRoots(toRemove, toAdd.keySet(), null);
    for (LocalFileSystem.WatchRequest request : requests) {
      final VcsDirectoryMapping mapping = toAdd.get(request.getRootPath());
      if (mapping != null) {
        myDirectoryMappingWatches.put(mapping, request);
      }
      else {
        LOG.error("root=" + request.getRootPath() + " toAdd=" + toAdd.keySet());
      }
    }
  }
}
