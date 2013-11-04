package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Nadya Zabrodina
 */
public class VcsRootDetector {
  private static final int MAXIMUM_SCAN_DEPTH = 2;

  @NotNull private final Project myProject;
  @NotNull private final ProjectRootManager myProjectManager;
  @NotNull private final ProjectLevelVcsManager myVcsManager;

  public VcsRootDetector(@NotNull Project project) {
    myProject = project;
    myProjectManager = ProjectRootManager.getInstance(project);
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  @NotNull
  public VcsRootDetectInfo detect() {
    return detect(myProject.getBaseDir());
  }

  @NotNull
  public VcsRootDetectInfo detect(@Nullable VirtualFile startDir) {
    if (startDir == null) {
      return new VcsRootDetectInfo(Collections.<VcsRoot>emptyList(), false, false);
    }

    final Set<VcsRoot> roots = scanForRootsInsideDir(startDir);
    roots.addAll(scanForRootsInContentRoots());
    for (VcsRoot root : roots) {
      if (startDir.equals(root.getPath())) {
        return new VcsRootDetectInfo(roots, true, false);
      }
    }
    List<VcsRoot> rootsAbove = scanForSingleRootAboveDir(startDir);
    if (!rootsAbove.isEmpty()) {
      roots.addAll(rootsAbove);
      return new VcsRootDetectInfo(roots, true, true);
    }
    return new VcsRootDetectInfo(roots, false, false);
  }

  @NotNull
  private Set<VcsRoot> scanForRootsInContentRoots() {
    Set<VcsRoot> gitRoots = new HashSet<VcsRoot>();
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
      gitRoots.addAll(rootsInsideRoot);
    }
    return gitRoots;
  }

  @NotNull
  private Set<VcsRoot> scanForRootsInsideDir(@NotNull final VirtualFile dir, final int depth) {
    final Set<VcsRoot> roots = new HashSet<VcsRoot>();
    if (depth > MAXIMUM_SCAN_DEPTH) {
      // performance optimization via limitation: don't scan deep though the whole VFS, 2 levels under a content root is enough
      return roots;
    }

    if (myProject.isDisposed() || !dir.isDirectory()) {
      return roots;
    }
    List<AbstractVcs> vcsList = getVcsListFor(dir);
    for (AbstractVcs vcs : vcsList) {
      roots.add(new VcsRoot(vcs, dir));
    }
    for (VirtualFile child : dir.getChildren()) {
      roots.addAll(scanForRootsInsideDir(child, depth + 1));
    }
    return roots;
  }

  @NotNull
  private Set<VcsRoot> scanForRootsInsideDir(@NotNull VirtualFile dir) {
    return scanForRootsInsideDir(dir, 0);
  }

  @NotNull
  private List<VcsRoot> scanForSingleRootAboveDir(@NotNull final VirtualFile dir) {
    List<VcsRoot> roots = new ArrayList<VcsRoot>();
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
    VcsRootChecker[] checkers = Extensions.getExtensions(VcsRootChecker.EXTENSION_POINT_NAME);
    List<AbstractVcs> vcsList = new ArrayList<AbstractVcs>();
    for (VcsRootChecker checker : checkers) {
      if (checker.isRoot(dir.getPath())) {
        vcsList.add(myVcsManager.findVcsByName(checker.getSupportedVcs().getName()));
      }
    }
    return vcsList;
  }
}
