/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsRootDetectorImpl implements VcsRootDetector {
  private static final Logger LOG = Logger.getInstance(VcsRootDetectorImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final ProjectRootManager myProjectManager;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final VcsRootChecker[] myCheckers;

  public VcsRootDetectorImpl(@NotNull Project project,
                             @NotNull ProjectRootManager projectRootManager,
                             @NotNull ProjectLevelVcsManager projectLevelVcsManager) {
    myProject = project;
    myProjectManager = projectRootManager;
    myVcsManager = projectLevelVcsManager;
    myCheckers = Extensions.getExtensions(VcsRootChecker.EXTENSION_POINT_NAME);
  }

  @NotNull
  public Collection<VcsRoot> detect() {
    return detect(myProject.getBaseDir());
  }

  @NotNull
  public Collection<VcsRoot> detect(@Nullable VirtualFile startDir) {
    if (startDir == null || myCheckers.length == 0) {
      return Collections.emptyList();
    }

    final Set<VcsRoot> roots = scanForRootsInsideDir(startDir);
    roots.addAll(scanForRootsInContentRoots());
    for (VcsRoot root : roots) {
      if (startDir.equals(root.getPath())) {
        return roots;
      }
    }
    List<VcsRoot> rootsAbove = scanForSingleRootAboveDir(startDir);
    roots.addAll(rootsAbove);
    return roots;
  }

  @NotNull
  private Set<VcsRoot> scanForRootsInContentRoots() {
    Set<VcsRoot> vcsRoots = new HashSet<>();
    if (myProject.isDisposed()) return vcsRoots;

    VirtualFile[] roots = myProjectManager.getContentRoots();
    for (VirtualFile contentRoot : roots) {

      Set<VcsRoot> rootsInsideRoot = scanForRootsInsideDir(contentRoot);
      boolean shouldScanAbove = true;
      for (VcsRoot root : rootsInsideRoot) {
        if (contentRoot.equals(root.getPath())) {
          shouldScanAbove = false;
        }
      }
      if (shouldScanAbove) {
        List<VcsRoot> rootsAbove = scanForSingleRootAboveDir(contentRoot);
        rootsInsideRoot.addAll(rootsAbove);
      }
      vcsRoots.addAll(rootsInsideRoot);
    }
    return vcsRoots;
  }

  @NotNull
  private Set<VcsRoot> scanForRootsInsideDir(@NotNull final VirtualFile dir, final int depth) {
    LOG.debug("Scanning inside [" + dir + "], depth = " + depth);
    final Set<VcsRoot> roots = new HashSet<>();
    if (depthLimitExceeded(depth)) {
      return roots;
    }

    if (ReadAction.compute(() -> myProject.isDisposed() || !dir.isDirectory() || myProjectManager.getFileIndex().isExcluded(dir))) {
      return roots;
    }
    List<AbstractVcs> vcsList = getVcsListFor(dir);
    LOG.debug("Found following VCSs: " + vcsList);
    for (AbstractVcs vcs : vcsList) {
      roots.add(new VcsRoot(vcs, dir));
    }
    for (VirtualFile child : dir.getChildren()) {
      roots.addAll(scanForRootsInsideDir(child, depth + 1));
    }
    return roots;
  }

  private static boolean depthLimitExceeded(int depth) {
    int maxDepth = Registry.intValue("vcs.root.detector.folder.depth");
    return maxDepth >= 0 && maxDepth < depth;
  }

  @NotNull
  private Set<VcsRoot> scanForRootsInsideDir(@NotNull VirtualFile dir) {
    return scanForRootsInsideDir(dir, 0);
  }

  @NotNull
  private List<VcsRoot> scanForSingleRootAboveDir(@NotNull final VirtualFile dir) {
    List<VcsRoot> roots = new ArrayList<>();
    if (myProject.isDisposed()) {
      return roots;
    }

    VirtualFile par = dir.getParent();
    while (par != null) {
      List<AbstractVcs> vcsList = getVcsListFor(par);
      for (AbstractVcs vcs : vcsList) {
        roots.add(new VcsRoot(vcs, par));
      }
      if (!roots.isEmpty()) {
        return roots;
      }
      par = par.getParent();
    }
    return roots;
  }

  @NotNull
  private List<AbstractVcs> getVcsListFor(@NotNull VirtualFile dir) {
    List<AbstractVcs> vcsList = new ArrayList<>();
    for (VcsRootChecker checker : myCheckers) {
      if (checker.isRoot(dir.getPath())) {
        vcsList.add(myVcsManager.findVcsByName(checker.getSupportedVcs().getName()));
      }
    }
    return vcsList;
  }
}
