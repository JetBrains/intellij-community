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

public class VcsRootDetectorImpl implements VcsRootDetector {
  private static final Logger LOG = Logger.getInstance(VcsRootDetectorImpl.class);

  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManager myVcsManager;

  @Nullable private Collection<DetectedVcsRoot> myDetectedRoots;
  @NotNull private final Object LOCK = new Object();

  public VcsRootDetectorImpl(@NotNull Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
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
    if (startDir == null) return Collections.emptyList();

    return Collections.unmodifiableSet(scanForDirectory(startDir));
  }

  @Override
  @NotNull
  public Collection<VcsRoot> getOrDetect() {
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

    Set<VcsRoot> detectedRoots = new HashSet<>(scanForRootsInsideDir(dirToScan, null));
    detectedRoots.addAll(scanForRootsAboveDirs(Collections.singletonList(dirToScan), detectedRoots));
    return detectedRoots;
  }

  @NotNull
  private Collection<VcsRoot> scanForRootsInContentRoots() {
    if (myProject.isDisposed()) return Collections.emptyList();
    if (!VcsRootChecker.EXTENSION_POINT_NAME.hasAnyExtensions()) {
      return Collections.emptyList();
    }

    List<VirtualFile> contentRoots = ContainerUtil.newArrayList(ProjectRootManager.getInstance(myProject).getContentRoots());

    VirtualFile baseDir = myProject.getBaseDir();
    if (baseDir != null && !contentRoots.contains(baseDir)) {
      contentRoots.add(baseDir);
    }

    Set<VcsRoot> detectedRoots = new HashSet<>();
    Set<VirtualFile> skipDirs = new HashSet<>();

    // process inner content roots first
    ContainerUtil.sort(contentRoots, Comparator.comparing(root -> -root.getPath().length()));
    for (VirtualFile dir : contentRoots) {
      detectedRoots.addAll(scanForRootsInsideDir(dir, skipDirs));
      skipDirs.add(dir);
    }

    detectedRoots.addAll(scanForRootsAboveDirs(contentRoots, detectedRoots));

    return detectedRoots;
  }

  private Set<VcsRoot> scanForRootsInsideDir(@NotNull VirtualFile root, @Nullable Set<VirtualFile> skipDirs) {
    Set<VcsRoot> roots = new HashSet<>();
    VcsRootScanner.visitDirsRecursivelyWithoutExcluded(myProject, ProjectRootManager.getInstance(myProject), root, dir -> {
      if (skipDirs != null && skipDirs.contains(dir)) {
        return SKIP_CHILDREN;
      }

      VcsRoot vcsRoot = getVcsRootFor(dir);
      if (vcsRoot != null) {
        LOG.debug("Found VCS " + vcsRoot.getVcs() + " in " + vcsRoot.getPath() + " under " + root.getPath());
        roots.add(vcsRoot);
      }
      return CONTINUE;
    });
    return roots;
  }

  @NotNull
  private Collection<VcsRoot> scanForRootsAboveDirs(@NotNull Collection<VirtualFile> dirsToScan,
                                                    @NotNull Collection<VcsRoot> detectedRoots) {
    HashSet<VcsRoot> result = new HashSet<>();

    Set<VirtualFile> skipDirs = new HashSet<>();
    for (VcsRoot root : detectedRoots) {
      skipDirs.add(root.getPath());
    }
    ContainerUtil.addIfNotNull(skipDirs, VfsUtil.getUserHomeDir()); // ignore mappings in ~/ and above

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
    if (VcsRootScanner.isUnderIgnoredDirectory(myProject, ignorePattern, root)) return null;

    VirtualFile parent = root;
    while (parent != null) {
      if (!skipDirs.add(parent)) break; // do not check same directory twice
      VcsRoot vcsRoot = getVcsRootFor(parent, root);
      if (vcsRoot != null) {
        LOG.debug("Found VCS " + vcsRoot.getVcs() + " in " + vcsRoot.getPath() + " above " + root.getPath());
        return vcsRoot;
      }

      parent = parent.getParent();
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
