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
package git4idea.repo;

import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public class GitRepositoryManager extends AbstractRepositoryManager<GitRepository> {

  @NotNull private final GitPlatformFacade myPlatformFacade;

  public GitRepositoryManager(@NotNull Project project, @NotNull GitPlatformFacade platformFacade,
                              @NotNull ProjectLevelVcsManager vcsManager) {
    super(project, vcsManager, platformFacade.getVcs(project), GitUtil.DOT_GIT);
    myPlatformFacade = platformFacade;
  }

  @NotNull
  @Override
  protected GitRepository createRepository(@NotNull VirtualFile root) {
    return GitRepositoryImpl.getFullInstance(root, myProject, myPlatformFacade, this);
  }
}
