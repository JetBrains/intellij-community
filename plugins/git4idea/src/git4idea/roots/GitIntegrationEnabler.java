/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.Notificator;
import git4idea.PlatformFacade;
import git4idea.commands.Git;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.util.text.StringUtil.pluralize;
import static git4idea.util.GitUIUtil.joinRootsPaths;

/**
 * @author Kirill Likhodedov
 */
public class GitIntegrationEnabler {

  private final @NotNull Project myProject;
  private final @NotNull Git myGit;
  private final @NotNull PlatformFacade myPlatformFacade;

  private static final Logger LOG = Logger.getInstance(GitIntegrationEnabler.class);

  public GitIntegrationEnabler(@NotNull Project project, @NotNull Git git, @NotNull PlatformFacade platformFacade) {
    myProject = project;
    myGit = git;
    myPlatformFacade = platformFacade;
  }

  public void enable(@NotNull GitRootDetectInfo detectInfo) {
    Notificator notificator = myPlatformFacade.getNotificator(myProject);

    if (detectInfo.totallyUnderGit()) {
      Collection<VirtualFile> roots = detectInfo.getRoots();
      assert !roots.isEmpty();
      if (roots.size() > 1 || detectInfo.projectIsBelowGit()) {
        notificator.notifySuccess("", String.format("Added Git %s: %s", pluralize("root", roots.size()), joinRootsPaths(roots)));
      }
      addVcsRoots(roots);
    }
    else if (detectInfo.empty()) {
      VirtualFile projectDir = myProject.getBaseDir();
      assert projectDir != null : "Base dir is unexpectedly null for project: " + myProject;
      try {
        myGit.init(myProject, projectDir);
        addVcsRoots(Collections.singleton(projectDir));
        notificator.notifySuccess("", "Created Git repository in \n" + projectDir.getPresentableUrl());
      }
      catch (VcsException e) {
        notificator.notifyError("Couldn't git init " + projectDir.getPresentableUrl(), e.getMessage());
        LOG.error(e);
      }
    }
    else {
      // TODO show dialog
    }
  }

  private void addVcsRoots(@NotNull Collection<VirtualFile> roots) {
    ProjectLevelVcsManager vcsManager = myPlatformFacade.getVcsManager(myProject);
    AbstractVcs vcs = myPlatformFacade.getVcs(myProject);
    List<VirtualFile> currentGitRoots = Arrays.asList(vcsManager.getRootsUnderVcs(vcs));

    List<VcsDirectoryMapping> mappings = new ArrayList<VcsDirectoryMapping>(vcsManager.getDirectoryMappings(vcs));

    for (VirtualFile root : roots) {
      if (!currentGitRoots.contains(root)) {
        mappings.add(new VcsDirectoryMapping(root.getPath(), vcs.getName()));
      }
    }
    vcsManager.setDirectoryMappings(mappings);
  }
}
