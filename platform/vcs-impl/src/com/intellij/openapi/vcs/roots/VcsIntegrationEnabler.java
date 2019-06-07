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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static java.util.stream.Collectors.toList;

public abstract class VcsIntegrationEnabler {

  @NotNull protected final Project myProject;
  @NotNull private final AbstractVcs myVcs;

  protected VcsIntegrationEnabler(@NotNull AbstractVcs vcs) {
    myProject = vcs.getProject();
    myVcs = vcs;
  }

  public void enable(@NotNull Collection<? extends VcsRoot> vcsRoots) {
    Collection<VirtualFile> roots = vcsRoots.stream().
      filter(root -> {
        AbstractVcs vcs = root.getVcs();
        return vcs != null && vcs.getName().equals(myVcs.getName());
      }).
      map(VcsRoot::getPath).collect(toList());

    VirtualFile projectDir = myProject.getBaseDir();
    assert projectDir != null : "Base dir is unexpectedly null for project: " + myProject;

    if (roots.isEmpty()) {
      boolean succeeded = initOrNotifyError(projectDir);
      if (succeeded) {
        addVcsRoots(Collections.singleton(projectDir));
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

  @NotNull
  public static String joinRootsPaths(@NotNull Collection<? extends VirtualFile> roots) {
    return StringUtil.join(roots, VirtualFile::getPresentableUrl, ", ");
  }

  protected abstract boolean initOrNotifyError(@NotNull final VirtualFile projectDir);

  protected void notifyAddedRoots(Collection<? extends VirtualFile> roots) {
    String message = String.format("Added %s %s: %s", myVcs.getName(), pluralize("root", roots.size()), joinRootsPaths(roots));
    VcsNotifier.getInstance(myProject).notifySuccess(message);
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
