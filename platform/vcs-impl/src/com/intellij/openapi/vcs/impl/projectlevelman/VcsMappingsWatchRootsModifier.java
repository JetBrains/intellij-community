// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl.projectlevelman;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.WatchRoots;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Maintains a VFS file watch {@link WatchRoots.Token} for each non-{@code <Project>} VCS mapping
 * to ensure that file changes are detected for every VCS root even when the VCS is not included in the workspace.
 */
@ApiStatus.Internal
public final class VcsMappingsWatchRootsModifier implements Runnable, Disposable {
  private final Project myProject;
  private final NewMappings myNewMappings;
  private final WatchRoots myWatchRoots;

  private final Object LOCK = new Object();
  private Collection<WatchRoots.Token> myWatches = Collections.emptyList();
  private boolean myDisposed;

  public VcsMappingsWatchRootsModifier(@NotNull Project project,
                                       @NotNull NewMappings newMappings,
                                       @NotNull WatchRoots watchRoots) {
    myWatchRoots = watchRoots;
    myProject = project;
    myNewMappings = newMappings;

    Disposer.register(newMappings, this);
  }

  @Override
  public void dispose() {
    synchronized (LOCK) {
      myDisposed = true;

      myWatches.forEach(WatchRoots.Token::close);
      myWatches = Collections.emptyList();
    }
  }

  @Override
  public void run() {
    if (!Registry.is("vcs.watch.roots")) return;
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

      Collection<WatchRoots.Token> previous = myWatches;
      List<WatchRoots.Token> next = new ArrayList<>(newWatchedRoots.size());
      myWatchRoots.batch(() -> {
        newWatchedRoots.forEach(root -> next.add(myWatchRoots.watch(root, true)));
        previous.forEach(WatchRoots.Token::close);
      });
      myWatches = next;
    }
  }
}
