/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.vcs.log.Hash;
import git4idea.branch.GitBrancher;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import static git4idea.branch.GitBranchUtil.getNewBranchNameFromUser;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;

public class GitCreateNewBranchAction extends GitLogSingleCommitAction {

  @Override
  protected void actionPerformed(@NotNull GitRepository repository, @NotNull Hash commit) {
    Project project = repository.getProject();
    String name = getNewBranchNameFromUser(project, singleton(repository), "Checkout New Branch From " + commit.toShortString());
    if (name != null) {
      GitBrancher brancher = GitBrancher.getInstance(project);
      brancher.checkoutNewBranchStartingFrom(name, commit.asString(), singletonList(repository), null);
    }
  }
}
