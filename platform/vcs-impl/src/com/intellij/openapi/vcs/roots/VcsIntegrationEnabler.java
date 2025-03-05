// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.roots;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.ROOT_ADDED;
import static java.util.stream.Collectors.toList;

public abstract class VcsIntegrationEnabler {
  protected final @NotNull Project myProject;
  private final @NotNull AbstractVcs myVcs;
  private final @Nullable VirtualFile myTargetDirectory;

  @SuppressWarnings("unused") // Used in 3rd-party plugins
  protected VcsIntegrationEnabler(@NotNull AbstractVcs vcs) {
    this(vcs, null);
  }

  protected VcsIntegrationEnabler(@NotNull AbstractVcs vcs, @Nullable VirtualFile targetDirectory) {
    myProject = vcs.getProject();
    myVcs = vcs;
    myTargetDirectory = targetDirectory;
  }

  public void detectAndEnable() {
    Collection<VcsRoot> roots = myTargetDirectory != null
                                ? VcsRootDetector.getInstance(myProject).detect(myTargetDirectory)
                                : VcsRootDetector.getInstance(myProject).detect();
    enable(roots);
  }

  public void enable(@NotNull Collection<? extends VcsRoot> vcsRoots) {
    Collection<VirtualFile> roots = vcsRoots.stream().
      filter(root -> {
        AbstractVcs vcs = root.getVcs();
        return vcs != null && vcs.getName().equals(myVcs.getName());
      }).
      map(VcsRoot::getPath).collect(toList());

    VirtualFile directoryToInitVcs = myTargetDirectory != null ? myTargetDirectory :  ProjectUtil.guessProjectDir(myProject);
    assert directoryToInitVcs != null : "Base dir is unexpectedly null for project: " + myProject;

    if (roots.isEmpty()) {
      boolean succeeded = initOrNotifyError(directoryToInitVcs);
      if (succeeded) {
        addVcsRoots(Collections.singleton(directoryToInitVcs));
      }
    }
    else {
      if (roots.size() > 1 || isProjectBelowVcs(roots)) {
        notifyAddedRoots(roots);
      }
      addVcsRoots(roots);
    }
  }

  private boolean isProjectBelowVcs(@NotNull Collection<? extends VirtualFile> roots) {
    //check if there are vcs roots strictly above the project dir
    return ContainerUtil.exists(roots, root -> VfsUtilCore.isAncestor(root, myProject.getBaseDir(), true));
  }

  public static @NotNull String joinRootsPaths(@NotNull Collection<? extends VirtualFile> roots) {
    return StringUtil.join(roots, VirtualFile::getPresentableUrl, ", ");
  }

  protected abstract boolean initOrNotifyError(final @NotNull VirtualFile directory);

  protected void notifyAddedRoots(Collection<? extends VirtualFile> roots) {
    String message = VcsBundle.message("roots.notification.content.added.vcs.name.roots", myVcs.getName(), roots.size(), joinRootsPaths(roots));
    VcsNotifier.getInstance(myProject).notifySuccess(ROOT_ADDED, "", message);
  }

  private void addVcsRoots(@NotNull Collection<? extends VirtualFile> roots) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    List<VirtualFile> currentVcsRoots = Arrays.asList(vcsManager.getRootsUnderVcs(myVcs));

    List<VcsDirectoryMapping> mappings = new ArrayList<>(vcsManager.getDirectoryMappings(myVcs));

    for (VirtualFile root : roots) {
      if (!currentVcsRoots.contains(root)) {
        mappings.add(new VcsDirectoryMapping(root.getPath(), myVcs.getName()));
      }
    }
    vcsManager.setDirectoryMappings(mappings);
  }

  protected static void refreshVcsDir(@NotNull VirtualFile projectDir, @NotNull String vcsDirName) {
    LocalFileSystem.getInstance().refreshAndFindFileByPath(projectDir.getPath() + "/" + vcsDirName);
  }
}
