package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class VcsRootErrorsFinder {
  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManager myVcsManager;
  @NotNull private final VcsRootDetector myRootDetector;

  public VcsRootErrorsFinder(@NotNull Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myRootDetector = ServiceManager.getService(myProject, VcsRootDetector.class);
  }

  @NotNull
  public Collection<VcsRootError> find() {
    List<VcsDirectoryMapping> mappings = myVcsManager.getDirectoryMappings();
    Collection<VcsRoot> vcsRoots = myRootDetector.detect();
    Collection<VcsRootError> errors = new ArrayList<>();
    errors.addAll(findExtraMappings(mappings));
    errors.addAll(findUnregisteredRoots(mappings, vcsRoots));
    return errors;
  }

  @NotNull
  private Collection<VcsRootError> findUnregisteredRoots(@NotNull List<VcsDirectoryMapping> mappings,
                                                         @NotNull Collection<VcsRoot> vcsRoots) {
    Collection<VcsRootError> errors = new ArrayList<>();
    List<String> mappedPaths = mappingsToPathsWithSelectedVcs(mappings);
    for (VcsRoot root : vcsRoots) {
      VirtualFile virtualFileFromRoot = root.getPath();
      if (virtualFileFromRoot == null) {
        continue;
      }
      String vcsPath = virtualFileFromRoot.getPath();
      if (!mappedPaths.contains(vcsPath) && root.getVcs() != null) {
        errors.add(new VcsRootErrorImpl(VcsRootError.Type.UNREGISTERED_ROOT, vcsPath, root.getVcs().getName()));
      }
    }
    return errors;
  }

  @NotNull
  private Collection<VcsRootError> findExtraMappings(@NotNull List<VcsDirectoryMapping> mappings) {
    Collection<VcsRootError> errors = new ArrayList<>();
    for (VcsDirectoryMapping mapping : mappings) {
      if (!hasVcsChecker(mapping.getVcs())) {
        continue;
      }
      if (mapping.isDefaultMapping()) {
        if (!isRoot(mapping)) {
          errors.add(new VcsRootErrorImpl(VcsRootError.Type.EXTRA_MAPPING, VcsDirectoryMapping.PROJECT_CONSTANT, mapping.getVcs()));
        }
      }
      else {
        String mappedPath = mapping.systemIndependentPath();
        if (!isRoot(mapping)) {
          errors.add(new VcsRootErrorImpl(VcsRootError.Type.EXTRA_MAPPING, mappedPath, mapping.getVcs()));
        }
      }
    }
    return errors;
  }

  private static boolean hasVcsChecker(String vcs) {
    if (StringUtil.isEmptyOrSpaces(vcs)) {
      return false;
    }
    VcsRootChecker[] checkers = Extensions.getExtensions(VcsRootChecker.EXTENSION_POINT_NAME);
    for (VcsRootChecker checker : checkers) {
      if (vcs.equalsIgnoreCase(checker.getSupportedVcs().getName())) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static Collection<VirtualFile> vcsRootsToVirtualFiles(@NotNull Collection<VcsRoot> vcsRoots) {
    return ContainerUtil.map(vcsRoots, new Function<VcsRoot, VirtualFile>() {
      @Override
      public VirtualFile fun(VcsRoot root) {
        return root.getPath();
      }
    });
  }

  private List<String> mappingsToPathsWithSelectedVcs(@NotNull List<VcsDirectoryMapping> mappings) {
    List<String> paths = new ArrayList<>();
    for (VcsDirectoryMapping mapping : mappings) {
      if (StringUtil.isEmptyOrSpaces(mapping.getVcs())) {
        continue;
      }
      if (!mapping.isDefaultMapping()) {
        paths.add(mapping.systemIndependentPath());
      }
      else {
        String basePath = myProject.getBasePath();
        if (basePath != null) {
          paths.add(FileUtil.toSystemIndependentName(basePath));
        }
      }
    }
    return paths;
  }

  public static VcsRootErrorsFinder getInstance(Project project) {
    return new VcsRootErrorsFinder(project);
  }

  private boolean isRoot(@NotNull final VcsDirectoryMapping mapping) {
    VcsRootChecker[] checkers = Extensions.getExtensions(VcsRootChecker.EXTENSION_POINT_NAME);
    final String pathToCheck = mapping.isDefaultMapping() ? myProject.getBasePath() : mapping.getDirectory();
    return ContainerUtil.find(checkers, new Condition<VcsRootChecker>() {
      @Override
      public boolean value(VcsRootChecker checker) {
        return checker.getSupportedVcs().getName().equalsIgnoreCase(mapping.getVcs()) && checker.isRoot(pathToCheck);
      }
    }) != null;
  }
}
