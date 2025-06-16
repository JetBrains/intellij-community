// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.projectlevelman.AllVcses;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

import static com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx.MAPPING_DETECTION_LOG;
import static com.intellij.openapi.vfs.VirtualFileVisitor.CONTINUE;
import static com.intellij.openapi.vfs.VirtualFileVisitor.SKIP_CHILDREN;

final class VcsRootDetectorImpl implements VcsRootDetector {
  private static final Logger LOG = Logger.getInstance(VcsRootDetectorImpl.class);

  private final @NotNull Project myProject;
  private final @NotNull Object LOCK = new Object();
  private @Nullable Collection<DetectedVcsRoot> myDetectedRoots;

  VcsRootDetectorImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull Collection<VcsRoot> detect() {
    MAPPING_DETECTION_LOG.debug("VcsRootDetectorImpl.detect");
    synchronized (LOCK) {
      Activity activity = StartUpMeasurer.startActivity("VcsRootDetector.detect");
      Collection<VcsRoot> roots = scanForRootsInContentRoots();
      activity.end();

      myDetectedRoots = ContainerUtil.map(roots, DetectedVcsRoot::new);
      return roots;
    }
  }

  @Override
  public @NotNull Collection<VcsRoot> detect(@Nullable VirtualFile startDir) {
    MAPPING_DETECTION_LOG.debug("VcsRootDetectorImpl.detect root", startDir);
    if (startDir == null || !startDir.isInLocalFileSystem()) return Collections.emptyList();
    return Collections.unmodifiableSet(scanForDirectory(startDir));
  }

  @Override
  public @NotNull Collection<VcsRoot> getOrDetect() {
    MAPPING_DETECTION_LOG.debug("VcsRootDetectorImpl.getOrDetect");
    synchronized (LOCK) {
      if (myDetectedRoots != null) {
        return ContainerUtil.mapNotNull(myDetectedRoots, it -> it.toVcsRoot(myProject));
      }
      return detect();
    }
  }

  private @NotNull Set<VcsRoot> scanForDirectory(@NotNull VirtualFile dirToScan) {
    if (!VcsRootChecker.EXTENSION_POINT_NAME.hasAnyExtensions()) {
      return Collections.emptySet();
    }

    Set<VcsRoot> detectedRoots = new HashSet<>();
    Map<VirtualFile, Boolean> scannedDirs = new HashMap<>();

    detectedRoots.addAll(scanForRootsInsideDir(myProject, dirToScan, null, scannedDirs));
    detectedRoots.addAll(scanForRootsAboveDirs(Collections.singletonList(dirToScan), scannedDirs, detectedRoots));
    return deduplicate(detectedRoots);
  }

  private @NotNull Collection<VcsRoot> scanForRootsInContentRoots() {
    if (myProject.isDisposed() || !VcsRootChecker.EXTENSION_POINT_NAME.hasAnyExtensions()) {
      return Collections.emptyList();
    }

    List<VirtualFile> contentRoots = ContainerUtil.sorted(DefaultVcsRootPolicy.getInstance(myProject).getDefaultVcsRoots(),
                                                          Comparator.comparing(root -> root.getPath().length()));
    MAPPING_DETECTION_LOG.debug("VcsRootDetectorImpl.scanForRootsInContentRoots - contentRoots", contentRoots);

    Set<VcsRoot> detectedRoots = new HashSet<>();
    Set<VirtualFile> skipDirs = new HashSet<>();
    Map<VirtualFile, Boolean> scannedDirs = new HashMap<>();

    // process the inner content roots first
    for (VirtualFile dir : ContainerUtil.reverse(contentRoots)) {
      detectedRoots.addAll(scanForRootsInsideDir(myProject, dir, skipDirs, scannedDirs));
      skipDirs.add(dir);
    }

    // process the outer content roots first
    detectedRoots.addAll(scanForRootsAboveDirs(contentRoots, scannedDirs, detectedRoots));

    Set<VcsRoot> detectedAndKnownRoots = new HashSet<>(detectedRoots);
    detectedAndKnownRoots.addAll(Arrays.asList(ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots()));
    detectedRoots.addAll(scanDependentRoots(scannedDirs, detectedAndKnownRoots));

    return deduplicate(detectedRoots);
  }

  /**
   * @return a deduplicated set of {@code detectedRoots} with removed links pointing to the same canonical file.
   */
  private static @NotNull Set<VcsRoot> deduplicate(@NotNull Set<VcsRoot> detectedRoots) {
    if (detectedRoots.size() <= 1) return detectedRoots;

    Set<VcsRoot> result = new HashSet<>();

    Set<VirtualFile> processedCanonicalRoots = new HashSet<>();
    Set<VcsRoot> rootsUnderSymlink = new HashSet<>();
    for (VcsRoot root : detectedRoots) {
      VirtualFile path = root.getPath();
      VirtualFile canonicalPath = path.getCanonicalFile();
      if (canonicalPath != null && !path.equals(canonicalPath)) {
        rootsUnderSymlink.add(root);
      } else {
        processedCanonicalRoots.add(path);
        result.add(root);
      }
    }

    if (rootsUnderSymlink.isEmpty()) return detectedRoots;

    for (VcsRoot root : ContainerUtil.sorted(rootsUnderSymlink, Comparator.comparing(root -> root.getPath().toNioPath()))) {
      VirtualFile canonicalFile = root.getPath().getCanonicalFile();
      if (processedCanonicalRoots.contains(canonicalFile)) {
        LOG.debug("Skipping duplicate VCS root %s: root for canonical file '%s' is already detected".formatted(root, canonicalFile));
      } else {
        processedCanonicalRoots.add(canonicalFile);
        result.add(root);
      }
    }

    return result;
  }

  private @NotNull Set<VcsRoot> scanForRootsInsideDir(@NotNull Project project,
                                                      @NotNull VirtualFile root,
                                                      @Nullable Set<? extends VirtualFile> skipDirs,
                                                      @NotNull Map<VirtualFile, Boolean> scannedDirs) {
    Set<VcsRoot> result = new HashSet<>();
    VcsRootScanner.visitDirsRecursivelyWithoutExcluded(project, root, false, dir -> {
      if (skipDirs != null && skipDirs.contains(dir)) {
        return SKIP_CHILDREN;
      }

      if (scannedDirs.containsKey(dir)) return CONTINUE;

      VcsRoot vcsRoot = getVcsRootFor(dir, null);
      scannedDirs.put(dir, vcsRoot != null);

      if (vcsRoot != null) {
        LOG.debug("Found VCS ", vcsRoot.getVcs(), " in ", vcsRoot.getPath(), " under ", root);
        result.add(vcsRoot);
      }
      return CONTINUE;
    });
    return result;
  }

  private @NotNull Collection<VcsRoot> scanForRootsAboveDirs(@NotNull Collection<? extends VirtualFile> dirsToScan,
                                                             @NotNull Map<VirtualFile, Boolean> scannedDirs,
                                                             @NotNull Collection<VcsRoot> detectedRoots) {
    Set<VirtualFile> skipDirs = new HashSet<>();
    for (VcsRoot root : detectedRoots) {
      skipDirs.add(root.getPath());
    }

    // ignore mappings in ~/ and above
    ContainerUtil.addIfNotNull(skipDirs, VfsUtil.getUserHomeDir());

    Set<VcsRoot> result = new HashSet<>();
    for (VirtualFile dir : dirsToScan) {
      scanForRootsAboveDir(dir, scannedDirs, skipDirs, result);
    }
    return result;
  }

  private void scanForRootsAboveDir(@NotNull VirtualFile root,
                                    @NotNull Map<VirtualFile, Boolean> scannedDirs,
                                    @NotNull Set<? super VirtualFile> skipDirs,
                                    @NotNull Set<? super VcsRoot> result) {
    Pattern ignorePattern = VcsRootScanner.parseDirIgnorePattern();
    if (VcsRootScanner.isUnderIgnoredDirectory(myProject, ignorePattern, root)) {
      return;
    }

    VirtualFile parent = root.isDirectory() ? root : root.getParent();
    while (parent != null) {
      // do not check same directory twice.
      // NB: we ignore differences in 'dirToCheckForIgnore' here and might miss some roots, that ignore one content root but not the other.
      if (!skipDirs.add(parent)) return;
      if (scannedDirs.get(parent) == Boolean.TRUE) return;

      if (!scannedDirs.containsKey(parent)) {
        VcsRoot vcsRoot = getVcsRootFor(parent, root);
        scannedDirs.put(parent, vcsRoot != null);

        if (vcsRoot != null) {
          LOG.debug("Found VCS ", vcsRoot.getVcs(), " in ", vcsRoot.getPath(), " above ", root);
          result.add(vcsRoot);
          return;
        }
      }

      parent = parent.getParent();
    }
  }

  private @NotNull Collection<VcsRoot> scanDependentRoots(@NotNull Map<VirtualFile, Boolean> scannedDirs,
                                                          @NotNull Collection<VcsRoot> detectedRoots) {
    Set<VcsRoot> result = new HashSet<>();
    for (VcsRoot root : detectedRoots) {
      VcsRootChecker rootChecker = VcsRootChecker.EXTENSION_POINT_NAME.findFirstSafe(checker -> {
        return root.getVcs() != null && Objects.equals(checker.getSupportedVcs(), root.getVcs().getKeyInstanceMethod());
      });
      if (rootChecker == null) continue;

      List<VirtualFile> dependentRoots = rootChecker.suggestDependentRoots(root.getPath());
      for (VirtualFile dependentRoot : dependentRoots) {
        if (scannedDirs.containsKey(dependentRoot)) continue;

        VcsRoot vcsRoot = detectVcsRootBy(dependentRoot, null, rootChecker);
        scannedDirs.put(dependentRoot, vcsRoot != null);

        if (vcsRoot != null) {
          LOG.debug("Found VCS ", vcsRoot.getVcs(), " in ", vcsRoot.getPath(), "dependent on", root);
          result.add(vcsRoot);
        }
      }
    }

    if (!result.isEmpty()) {
      // handle recursive dependencies
      result.addAll(scanDependentRoots(scannedDirs, result));
    }

    return result;
  }

  private @Nullable VcsRoot getVcsRootFor(@NotNull VirtualFile maybeRoot, @Nullable VirtualFile dirToCheckForIgnore) {
    ProgressManager.checkCanceled();
    return VcsRootChecker.EXTENSION_POINT_NAME.computeSafeIfAny(checker -> {
      return detectVcsRootBy(maybeRoot, dirToCheckForIgnore, checker);
    });
  }

  private @Nullable VcsRoot detectVcsRootBy(@NotNull VirtualFile maybeRoot,
                                            @Nullable VirtualFile dirToCheckForIgnore,
                                            @NotNull VcsRootChecker checker) {
    if (!maybeRoot.isInLocalFileSystem()) return null;
    if (!checker.isRoot(maybeRoot)) return null;
    if (dirToCheckForIgnore != null && checker.isIgnored(maybeRoot, dirToCheckForIgnore)) return null;

    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).findVcsByName(checker.getSupportedVcs().getName());
    if (vcs == null) return null;

    return new VcsRoot(vcs, maybeRoot);
  }

  private static final class DetectedVcsRoot {
    private final @Nullable String myVcsName;
    private final @NotNull VirtualFile myPath;

    private DetectedVcsRoot(@NotNull VcsRoot root) {
      AbstractVcs vcs = root.getVcs();
      myVcsName = vcs != null ? vcs.getName() : null;
      myPath = root.getPath();
    }

    public @Nullable VcsRoot toVcsRoot(@NotNull Project project) {
      if (myVcsName == null) return null;
      AbstractVcs vcs = AllVcses.getInstance(project).getByName(myVcsName);
      return vcs != null ? new VcsRoot(vcs, myPath) : null;
    }
  }
}
