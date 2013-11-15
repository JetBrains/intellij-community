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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.roots.VcsRootDetectInfo;
import com.intellij.openapi.vcs.roots.VcsRootErrorsFinder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import git4idea.GitPlatformFacade;
import git4idea.GitVcs;
import git4idea.Notificator;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.dvcs.DvcsUtil.joinRootsPaths;
import static com.intellij.openapi.util.text.StringUtil.pluralize;

/**
 * @author Kirill Likhodedov
 */
public class GitIntegrationEnabler {

  private final @NotNull Project myProject;
  private final @NotNull Git myGit;
  private final @NotNull GitPlatformFacade myPlatformFacade;

  private static final Logger LOG = Logger.getInstance(GitIntegrationEnabler.class);

  public GitIntegrationEnabler(@NotNull Project project, @NotNull Git git, @NotNull GitPlatformFacade platformFacade) {
    myProject = project;
    myGit = git;
    myPlatformFacade = platformFacade;
  }

  public void enable(@NotNull VcsRootDetectInfo detectInfo) {
    Notificator notificator = myPlatformFacade.getNotificator(myProject);
    Collection<VcsRoot> gitRoots = ContainerUtil.filter(detectInfo.getRoots(), new Condition<VcsRoot>() {
      @Override
      public boolean value(VcsRoot root) {
        AbstractVcs gitVcs = root.getVcs();
        return gitVcs != null && gitVcs.getName().equals(GitVcs.NAME);
      }
    });
    Collection<VirtualFile> roots = VcsRootErrorsFinder.vcsRootsToVirtualFiles(gitRoots);
    VirtualFile projectDir = myProject.getBaseDir();
    assert projectDir != null : "Base dir is unexpectedly null for project: " + myProject;

    if (gitRoots.isEmpty()) {
      boolean succeeded = gitInitOrNotifyError(notificator, projectDir);
      if (succeeded) {
        addVcsRoots(Collections.singleton(projectDir));
      }
    }
    else {
      assert !roots.isEmpty();
      if (roots.size() > 1 || detectInfo.projectIsBelowVcs()) {
        notifyAddedRoots(notificator, roots);
      }
      addVcsRoots(roots);
    }
  }

  private static void notifyAddedRoots(Notificator notificator, Collection<VirtualFile> roots) {
    notificator.notifySuccess("", String.format("Added Git %s: %s", pluralize("root", roots.size()), joinRootsPaths(roots)));
  }

  private boolean gitInitOrNotifyError(@NotNull Notificator notificator, @NotNull final VirtualFile projectDir) {
    GitCommandResult result = myGit.init(myProject, projectDir);
    if (result.success()) {
      refreshGitDir(projectDir);
      notificator.notifySuccess("", "Created Git repository in " + projectDir.getPresentableUrl());
      return true;
    }
    else {
      if (((GitVcs)myPlatformFacade.getVcs(myProject)).getExecutableValidator().checkExecutableAndNotifyIfNeeded()) {
        notificator.notifyError("Couldn't git init " + projectDir.getPresentableUrl(), result.getErrorOutputAsHtmlString());
        LOG.info(result.getErrorOutputAsHtmlString());
      }
      return false;
    }
  }

  private void refreshGitDir(final VirtualFile projectDir) {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        myPlatformFacade.runReadAction(new Runnable() {
          @Override
          public void run() {
            myPlatformFacade.getLocalFileSystem().refreshAndFindFileByPath(projectDir.getPath() + "/.git");
          }
        });
      }
    });
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
