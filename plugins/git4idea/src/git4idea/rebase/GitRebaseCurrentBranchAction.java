/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.VcsFullCommitDetails;
import git4idea.branch.GitBranchUtil;
import git4idea.branch.GitRebaseParams;
import git4idea.repo.GitRepository;
import git4idea.reset.GitOneCommitPerRepoLogAction;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static java.util.Collections.singletonList;

/**
 * @author Dmitry Zhuravlev
 *         Date:  22.05.2017
 */
public class GitRebaseCurrentBranchAction extends GitOneCommitPerRepoLogAction {

  @Override
  protected void actionPerformed(@NotNull Project project, @NotNull Map<GitRepository, VcsFullCommitDetails> commits) {
    final GitRepository repository = GitBranchUtil.getCurrentRepository(project);
    final VcsFullCommitDetails commit = commits.get(repository);
    if (repository == null || commit == null) return;
    final String commitHashString = commit.getId().asString();
    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Rebasing...") {
      public void run(@NotNull ProgressIndicator indicator) {
        GitRebaseUtils.rebase(project, singletonList(repository), new GitRebaseParams(null, null, commitHashString, true, true), indicator);
      }
    });
  }
}
