/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import git4idea.GitPlatformFacade;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public class GitRenameBranchOperation extends GitBranchOperation {
  @NotNull private final VcsNotifier myNotifier;
  @NotNull private final String myCurrentName;
  @NotNull private final String myNewName;

  public GitRenameBranchOperation(@NotNull Project project,
                                  @NotNull GitPlatformFacade facade,
                                  @NotNull Git git,
                                  @NotNull GitBranchUiHandler uiHandler,
                                  @NotNull String currentName,
                                  @NotNull String newName,
                                  @NotNull List<GitRepository> repositories) {
    super(project, facade, git, uiHandler, repositories);
    myCurrentName = currentName;
    myNewName = newName;
    myNotifier = VcsNotifier.getInstance(myProject);
  }

  @Override
  protected void execute() {
    while (hasMoreRepositories()) {
      GitRepository repository = next();
      GitCommandResult result = myGit.renameBranch(repository, myCurrentName, myNewName);
      if (result.success()) {
        refresh(repository);
        markSuccessful(repository);
      }
      else {
        fatalError("Couldn't rename " + myCurrentName + " to " + myNewName, result.getErrorOutputAsJoinedString());
        return;
      }
    }
    notifySuccess();
  }

  @Override
  protected void rollback() {
    GitCompoundResult result = new GitCompoundResult(myProject);
    Collection<GitRepository> repositories = getSuccessfulRepositories();
    for (GitRepository repository : repositories) {
      result.append(repository, myGit.renameBranch(repository, myNewName, myCurrentName));
      refresh(repository);
    }
    if (result.totalSuccess()) {
      myNotifier.notifySuccess("Rollback Successful", "Renamed back to " + myCurrentName);
    }
    else {
      myNotifier.notifyError("Rollback Failed", result.getErrorOutputWithReposIndication());
    }
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    return String.format("Branch <b><code>%s</code></b> was renamed to <b><code>%s</code></b>", myCurrentName, myNewName);
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    return "However rename has succeeded for the following " + repositories() + ":<br/>" +
           successfulRepositoriesJoined() +
           "<br/>You may rollback (rename branch back to " + myCurrentName + ") not to let branches diverge.";
  }

  @NotNull
  @Override
  protected String getOperationName() {
    return "rename";
  }

  private static void refresh(@NotNull GitRepository repository) {
    repository.update();
  }
}
