// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.openapi.vfs.VirtualFileVisitor.CONTINUE;

public class VcsRootDetectorImpl implements VcsRootDetector {
  private static final Logger LOG = Logger.getInstance(VcsRootDetectorImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManager myVcsManager;

  @Nullable private Collection<VcsRoot> myDetectedRoots;
  @NotNull private final Object LOCK = new Object();

  public VcsRootDetectorImpl(@NotNull Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
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
    if (startDir == null || !VcsRootChecker.EXTENSION_POINT_NAME.hasAnyExtensions()) {
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

    for (VirtualFile contentRoot : ProjectRootManager.getInstance(myProject).getContentRoots()) {
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
    VcsRootScanner.visitDirsRecursivelyWithoutExcluded(myProject, ProjectRootManager.getInstance(myProject), root, dir -> {
      VcsRoot vcsRoot = getVcsRootFor(dir);
      if (vcsRoot != null) {
        LOG.debug("Found VCS " + vcsRoot.getVcs() + " in " + vcsRoot.getPath());
        roots.add(vcsRoot);
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

    VirtualFile userHomeDir = VfsUtil.getUserHomeDir();
    VirtualFile par = dir.getParent();
    while (par != null) {
      if (par.equals(userHomeDir)) break; // ignore mappings in ~/ and above
      VcsRoot vcsRoot = getVcsRootFor(par, dir);
      if (vcsRoot != null) return vcsRoot;
      par = par.getParent();
    }
    return null;
  }

  @Nullable
  private VcsRoot getVcsRootFor(@NotNull VirtualFile dir) {
    return getVcsRootFor(dir, null);
  }

  @Nullable
  private VcsRoot getVcsRootFor(@NotNull VirtualFile maybeRoot, @Nullable VirtualFile dirToCheckForIgnore) {
    ProgressManager.checkCanceled();
    String path = maybeRoot.getPath();
    return VcsRootChecker.EXTENSION_POINT_NAME.computeSafeIfAny(checker -> {
      if (checker.isRoot(path) && (dirToCheckForIgnore == null || !checker.isIgnored(maybeRoot, dirToCheckForIgnore))) {
        AbstractVcs vcs = myVcsManager.findVcsByName(checker.getSupportedVcs().getName());
        if (vcs != null) return new VcsRoot(vcs, maybeRoot);
        return null;
      }
      return null;
    });
  }
}
