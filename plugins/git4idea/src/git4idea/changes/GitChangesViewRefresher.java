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
package git4idea.changes;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangesViewRefresher;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

/**
 * Untracked files in Git are not queried within the normal refresh procedure - they are watched separately.
 * I.e. to make a full refresh when user presses "Refresh" in the Changes View it is needed to prepare untracked files for refresh as well.
 *
 * @author Kirill Likhodedov
 */
public class GitChangesViewRefresher implements ChangesViewRefresher {

  @Override
  public void refresh(@NotNull Project project) {
    GitRepositoryManager repositoryManager = ServiceManager.getService(project, GitRepositoryManager.class);
    for (GitRepository repository : repositoryManager.getRepositories()) {
      repository.getUntrackedFilesHolder().invalidate();
    }
  }
}
