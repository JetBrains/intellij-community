// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.vfs.VirtualFileVisitor.CONTINUE;
import static com.intellij.openapi.vfs.VirtualFileVisitor.SKIP_CHILDREN;

public class VcsRootDetectorImpl implements VcsRootDetector {
  private static final Logger LOG = Logger.getInstance(VcsRootDetectorImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final ProjectRootManager myProjectManager;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final List<VcsRootChecker> myCheckers;

  @Nullable private Collection<VcsRoot> myDetectedRoots;
  @NotNull private final Object LOCK = new Object();

  public VcsRootDetectorImpl(@NotNull Project project,
                             @NotNull ProjectRootManager projectRootManager,
                             @NotNull ProjectLevelVcsManager projectLevelVcsManager) {
    myProject = project;
    myProjectManager = projectRootManager;
    myVcsManager = projectLevelVcsManager;
    myCheckers = VcsRootChecker.EXTENSION_POINT_NAME.getExtensionList();
  }

  @Override
  @NotNull
  public Collection<VcsRoot> detect() {
    synchronized (LOCK) {
      myDetectedRoots = detect(myProject.getBaseDir());
      return myDetectedRoots;
    }
  }

  @Override
  @NotNull
  public Collection<VcsRoot> detect(@Nullable VirtualFile startDir) {
    return doDetect(startDir);
  }

  @NotNull
  private Collection<VcsRoot> doDetect(@Nullable VirtualFile startDir) {
    if (startDir == null || myCheckers.size() == 0) {
      return Collections.emptyList();
    }

    Set<VcsRoot> roots = scanForRootsInsideDir(startDir);
    if (shouldScanAbove(startDir, roots)) {
      VcsRoot rootAbove = scanForSingleRootAboveDir(startDir);
      if (rootAbove != null) roots.add(rootAbove);
    }
    roots.addAll(scanForRootsInContentRoots());
    return Collections.unmodifiableSet(roots);
  }

  @Override
  @NotNull
  public Collection<VcsRoot> getOrDetect() {
    synchronized (LOCK) {
      if (myDetectedRoots == null) {
        detect();
      }
      return myDetectedRoots;
    }
  }

  @NotNull
  private Set<VcsRoot> scanForRootsInContentRoots() {
    Set<VcsRoot> vcsRoots = new HashSet<>();
    if (myProject.isDisposed()) return vcsRoots;

    for (VirtualFile contentRoot : myProjectManager.getContentRoots()) {
      if (myProject.getBaseDir() != null && VfsUtilCore.isAncestor(myProject.getBaseDir(), contentRoot, false)) {
        continue;
      }

      Set<VcsRoot> vcsRootsInContentRoot = scanForRootsInsideDir(contentRoot);
      if (shouldScanAbove(contentRoot, vcsRootsInContentRoot)) {
        VcsRoot rootAbove = scanForSingleRootAboveDir(contentRoot);
        if (rootAbove != null) vcsRootsInContentRoot.add(rootAbove);
      }
      vcsRoots.addAll(vcsRootsInContentRoot);
    }
    return vcsRoots;
  }

  private Set<VcsRoot> scanForRootsInsideDir(@NotNull VirtualFile root) {
    Set<VcsRoot> roots = new HashSet<>();
    VcsRootScanner.visitDirsRecursivelyWithoutExcluded(myProject, myProjectManager, root, dir -> {
      if (Registry.is("vcs.root.detector.skip.vendor") && dir.getName().equalsIgnoreCase("vendor")) return SKIP_CHILDREN;
      AbstractVcs vcs = getVcsFor(dir);
      if (vcs != null) {
        LOG.debug("Found VCS " + vcs + " in " + dir);
        roots.add(new VcsRoot(vcs, dir));
      }
      return CONTINUE;
    });
    return roots;
  }

  private static boolean shouldScanAbove(@NotNull VirtualFile startDir, @NotNull Set<? extends VcsRoot> rootsInsideDir) {
    return rootsInsideDir.stream().noneMatch(it -> startDir.equals(it.getPath()));
  }

  @Nullable
  private VcsRoot scanForSingleRootAboveDir(@NotNull final VirtualFile dir) {
    if (myProject.isDisposed()) {
      return null;
    }

    ProgressManager.checkCanceled();
    VirtualFile par = dir.getParent();
    while (par != null && !par.equals(VfsUtil.getUserHomeDir())) {
      AbstractVcs vcs = getVcsFor(par, dir);
      if (vcs != null) return new VcsRoot(vcs, par);
      par = par.getParent();
    }
    return null;
  }

  @Nullable
  private AbstractVcs getVcsFor(@NotNull VirtualFile dir) {
    return getVcsFor(dir, null);
  }

  @Nullable
  private AbstractVcs getVcsFor(@NotNull VirtualFile maybeRoot, @Nullable VirtualFile dirToCheckForIgnore) {
    String path = maybeRoot.getPath();
    for (VcsRootChecker checker : myCheckers) {
      if (checker.isRoot(path) && (dirToCheckForIgnore == null || !checker.isIgnored(maybeRoot, dirToCheckForIgnore))) {
        return myVcsManager.findVcsByName(checker.getSupportedVcs().getName());
      }
    }
    return null;
  }
}
