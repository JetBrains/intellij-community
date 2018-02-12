/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.merge;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.branch.GitBranchUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

import static com.intellij.util.ObjectUtils.assertNotNull;
import static com.intellij.util.containers.ContainerUtil.filter;
import static git4idea.GitUtil.getRootsFromRepositories;

public class GitMerger {

  private final Project myProject;
  private final GitRepositoryManager myRepositoryManager;

  public GitMerger(@NotNull Project project) {
    myProject = project;
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);
  }

  @NotNull
  public Collection<VirtualFile> getMergingRoots() {
    return getRootsFromRepositories(filter(myRepositoryManager.getRepositories(),
                                           repository -> repository.getState() == Repository.State.MERGING));
  }

  public void mergeCommit(@NotNull Collection<VirtualFile> roots) throws VcsException {
    for (VirtualFile root : roots) {
      mergeCommit(root);
    }
  }

  public void mergeCommit(@NotNull VirtualFile root) throws VcsException {
    GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.COMMIT);
    handler.setStdoutSuppressed(false);

    File messageFile = assertNotNull(myRepositoryManager.getRepositoryForRoot(root)).getRepositoryFiles().getMergeMessageFile();
    if (!messageFile.exists()) {
      final GitBranch branch = GitBranchUtil.getCurrentBranch(myProject, root);
      final String branchName = branch != null ? branch.getName() : "";
      handler.addParameters("-m", "Merge branch '" + branchName + "' of " + root.getPresentableUrl() + " with conflicts.");
    } else {
      handler.addParameters("-F", messageFile.getAbsolutePath());
    }
    handler.endOptions();
    Git.getInstance().runCommand(handler).getOutputOrThrow();
  }

}
