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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.PlatformFacade;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * Detects actual Git roots and compares them to the ones registered in Settings | Version Control.
 *
 * @author Kirill Likhodedov
 */
class GitRootErrorsFinder {

  @NotNull private final Project myProject;
  private final PlatformFacade myPlatformFacade;

  GitRootErrorsFinder(@NotNull Project project, @NotNull PlatformFacade platformFacade) {
    myProject = project;
    myPlatformFacade = platformFacade;
  }

  @NotNull
  Collection<GitRootError> find() {
    ProjectLevelVcsManager vcsManager = myPlatformFacade.getVcsManager(myProject);
    Collection<VirtualFile> vcsRoots = Arrays.asList(vcsManager.getRootsUnderVcs(myPlatformFacade.getVcs(myProject)));
    Collection<VirtualFile> gitRoots = new GitRootDetector(myProject).detect().getRoots();
    Collection<GitRootError> errors = new ArrayList<GitRootError>();
    for (VirtualFile vcsRoot : vcsRoots) {
      if (!gitRoots.contains(vcsRoot)) {
        errors.add(new GitRootError(GitRootError.Type.EXTRA_ROOT, vcsRoot));
      }
    }
    for (VirtualFile gitRoot : gitRoots) {
      if (!vcsRoots.contains(gitRoot)) {
        errors.add(new GitRootError(GitRootError.Type.UNREGISTERED_ROOT, gitRoot));
      }
    }
    return errors;
  }

}
