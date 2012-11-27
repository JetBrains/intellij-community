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
package git4idea.annotate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.VcsAnnotationRefresher;
import git4idea.GitVcs;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryChangeListener;
import org.jetbrains.annotations.NotNull;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 11/26/12
 * Time: 2:11 PM
 */
public class GitRepositoryForAnnotationsListener {
  private final Project myProject;
  private final GitRepositoryChangeListener myListener;
  private ProjectLevelVcsManager myVcsManager;
  private GitVcs myVcs;

  public GitRepositoryForAnnotationsListener(Project project) {
    myProject = project;
    myListener = createListener();
    myVcs = GitVcs.getInstance(myProject);
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    project.getMessageBus().connect().subscribe(GitRepository.GIT_REPO_CHANGE, myListener);
  }

  private GitRepositoryChangeListener createListener() {
    return new GitRepositoryChangeListener() {
      @Override
      public void repositoryChanged(@NotNull GitRepository repository) {
        final VcsAnnotationRefresher refresher = myProject.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED);
        refresher.dirtyUnder(repository.getRoot());
      }
    };
  }
}
