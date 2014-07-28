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
package git4idea.test;

import com.intellij.dvcs.repo.RepoStateException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitPlatformFacade;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryImpl;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

public class GitTestRepositoryManager extends GitRepositoryManager {

  @NotNull private final Project myProject;
  @NotNull private final GitPlatformFacade myFacade;

  public GitTestRepositoryManager(@NotNull Project project,
                                  @NotNull GitPlatformFacade platformFacade,
                                  @NotNull ProjectLevelVcsManager vcsManager) {
    super(project, platformFacade, vcsManager);
    myProject = project;
    myFacade = platformFacade;
  }

  @NotNull
  @Override
  protected GitRepository createRepository(@NotNull VirtualFile root) {
    return new GitRepositoryImpl(root, myFacade, myProject, this, false) {
      @Override
      public void update() {
        try {
          super.update();
        }
        catch (RepoStateException e) {
          if (!Disposer.isDisposed(this)) { // project dir will simply be removed during dispose
            throw e;
          }
        }
      }
    };
  }

}
