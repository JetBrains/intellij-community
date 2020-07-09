// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

import static com.intellij.openapi.vfs.VirtualFileVisitor.CONTINUE;
import static com.intellij.openapi.vfs.VirtualFileVisitor.SKIP_CHILDREN;

final class VcsRootDetectorImpl implements VcsRootDetector {
  private static final Logger LOG = Logger.getInstance(VcsRootDetectorImpl.class);

  @NotNull private final Project myProject;

  @Nullable private Collection<DetectedVcsRoot> myDetectedRoots;
  @NotNull private final Object LOCK = new Object();

  VcsRootDetectorImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public Collection<VcsRoot> detect() {
    synchronized (LOCK) {
      Collection<VcsRoot> roots = scanForRootsInContentRoots();
      myDetectedRoots = ContainerUtil.map(roots, DetectedVcsRoot::new);
      return roots;
    }
  }

  @Override
  @NotNull
  public Collection<VcsRoot> detect(@Nullable VirtualFile startDir) {
    return startDir == null ? Collections.emptyList() : Collections.unmodifiableSet(scanForDirectory(startDir));
  }

  @Override
  public @NotNull Collection<VcsRoot> getOrDetect() {
    synchronized (LOCK) {
      if (myDetectedRoots != null) {
        return ContainerUtil.mapNotNull(myDetectedRoots, it -> it.toVcsRoot(myProject));
      }
      return detect();
    }
  }

  @NotNull
  private Set<VcsRoot> scanForDirectory(@NotNull VirtualFile dirToScan) {
    if (!VcsRootChecker.EXTENSION_POINT_NAME.hasAnyExtensions()) {
      return Collections.emptySet();
    }

    Set<VcsRoot> detectedRoots = new HashSet<>();
    scanForRootsInsideDir(myProject, dirToScan, null, detectedRoots);
    detectedRoots.addAll(scanForRootsAboveDirs(Collections.singletonList(dirToScan), detectedRoots));
    return detectedRoots;
  }

  @NotNull
  private Collection<VcsRoot> scanForRootsInContentRoots() {
    if (myProject.isDisposed() || !VcsRootChecker.EXTENSION_POINT_NAME.hasAnyExtensions()) {
      return Collections.emptyList();
    }

    List<VirtualFile> contentRoots = new ArrayList<>(Arrays.asList(ProjectRootManager.getInstance(myProject).getContentRoots()));

    VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir != null && !contentRoots.contains(baseDir)) {
      contentRoots.add(baseDir);
    }

    Set<VcsRoot> detectedRoots = new HashSet<>();
    Set<VirtualFile> skipDirs = new HashSet<>();

    // process inner content roots first
    contentRoots.sort(Comparator.comparing(root -> -root.getPath().length()));
    for (VirtualFile dir : contentRoots) {
      scanForRootsInsideDir(myProject, dir, skipDirs, detectedRoots);
      skipDirs.add(dir);
    }

    detectedRoots.addAll(scanForRootsAboveDirs(contentRoots, detectedRoots));
    return detectedRoots;
  }

  private void scanForRootsInsideDir(@NotNull Project project, @NotNull VirtualFile root, @Nullable Set<VirtualFile> skipDirs, @NotNull Set<VcsRoot> result) {
    VcsRootScanner.visitDirsRecursivelyWithoutExcluded(project, ProjectRootManager.getInstance(project), root, dir -> {
      if (skipDirs != null && skipDirs.contains(dir)) {
        return SKIP_CHILDREN;
      }

      VcsRoot vcsRoot = getVcsRootFor(dir, null);
      if (vcsRoot != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Found VCS " + vcsRoot.getVcs() + " in " + vcsRoot.getPath() + " under " + root.getPath());
        }
        result.add(vcsRoot);
      }
      return CONTINUE;
    });
  }

  @NotNull
  private Collection<VcsRoot> scanForRootsAboveDirs(@NotNull Collection<VirtualFile> dirsToScan,
                                                    @NotNull Collection<VcsRoot> detectedRoots) {
    Set<VirtualFile> skipDirs = new HashSet<>();
    for (VcsRoot root : detectedRoots) {
      skipDirs.add(root.getPath());
    }

    // ignore mappings in ~/ and above
    ContainerUtil.addIfNotNull(skipDirs, VfsUtil.getUserHomeDir());

    Set<VcsRoot> result = new HashSet<>();
    for (VirtualFile dir : dirsToScan) {
      VcsRoot root = scanForRootsAboveDir(dir, skipDirs);
      if (root != null) {
        result.add(root);
      }
    }
    return result;
  }

  @Nullable
  private VcsRoot scanForRootsAboveDir(@NotNull VirtualFile root, @NotNull Set<VirtualFile> skipDirs) {
    Pattern ignorePattern = VcsRootScanner.parseDirIgnorePattern();
    if (VcsRootScanner.isUnderIgnoredDirectory(myProject, ignorePattern, root)) {
      return null;
    }

    VirtualFile parent = root;
    while (parent != null) {
      // do not check same directory twice
      if (!skipDirs.add(parent)) {
        break;
      }
      VcsRoot vcsRoot = getVcsRootFor(parent, root);
      if (vcsRoot != null) {
        LOG.debug("Found VCS " + vcsRoot.getVcs() + " in " + vcsRoot.getPath() + " above " + root.getPath());
        return vcsRoot;
      }

      parent = parent.getParent();
    }
    return null;
  }

  private @Nullable VcsRoot getVcsRootFor(@NotNull VirtualFile maybeRoot, @Nullable VirtualFile dirToCheckForIgnore) {
    ProgressManager.checkCanceled();
    String path = maybeRoot.getPath();
    ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    return VcsRootChecker.EXTENSION_POINT_NAME.computeSafeIfAny(checker -> {
      if (checker.isRoot(path) && (dirToCheckForIgnore == null || !checker.isIgnored(maybeRoot, dirToCheckForIgnore))) {
        AbstractVcs vcs = projectLevelVcsManager.findVcsByName(checker.getSupportedVcs().getName());
        return vcs == null ? null : new VcsRoot(vcs, maybeRoot);
      }
      return null;
    });
  }

  private static final class DetectedVcsRoot {
    @Nullable private final String myVcsName;
    @NotNull private final VirtualFile myPath;

    private DetectedVcsRoot(@NotNull VcsRoot root) {
      AbstractVcs vcs = root.getVcs();
      myVcsName = vcs != null ? vcs.getName() : null;
      myPath = root.getPath();
    }

    @Nullable
    public VcsRoot toVcsRoot(@NotNull Project project) {
      if (myVcsName == null) return null;
      AbstractVcs vcs = AllVcses.getInstance(project).getByName(myVcsName);
      return vcs != null ? new VcsRoot(vcs, myPath) : null;
    }
  }
}
