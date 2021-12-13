// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

public final class FileWatchRequestModifier implements Runnable, Disposable {
  private final Project myProject;
  private final NewMappings myNewMappings;
  private final LocalFileSystem myLfs;

  private final Object LOCK = new Object();
  private Set<LocalFileSystem.WatchRequest> myWatches = Collections.emptySet();
  private boolean myDisposed;

  public FileWatchRequestModifier(@NotNull Project project, @NotNull NewMappings newMappings, @NotNull LocalFileSystem localFileSystem) {
    myLfs = localFileSystem;
    myProject = project;
    myNewMappings = newMappings;

    Disposer.register(newMappings, this);
  }

  @Override
  public void dispose() {
    synchronized (LOCK) {
      myDisposed = true;

      myLfs.removeWatchedRoots(myWatches);
      myWatches = Collections.emptySet();
    }
  }

  @Override
  public void run() {
    synchronized (LOCK) {
      if (myDisposed) return;
      if (!myProject.isInitialized()) return;

      Set<String> newWatchedRoots = CollectionFactory.createFilePathSet();
      for (VcsDirectoryMapping mapping : myNewMappings.getDirectoryMappings()) {
        // <Project> mappings are ignored because they should already be watched by the Project
        if (mapping.isDefaultMapping()) continue;

        AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).findVcsByName(mapping.getVcs());
        if (vcs != null && vcs.needsLFSWatchesForRoots()) {
          newWatchedRoots.add(FileUtil.toCanonicalPath(mapping.getDirectory()));
        }
      }

      myWatches = myLfs.replaceWatchedRoots(myWatches, newWatchedRoots, null);
    }
  }
}
