// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge;

import com.intellij.dvcs.repo.Repository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitBranch;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

import static com.intellij.util.containers.ContainerUtil.filter;
import static git4idea.GitUtil.getRootsFromRepositories;

public final class GitMerger {

  private final Project myProject;

  public GitMerger(@NotNull Project project) {
    myProject = project;
  }

  public @NotNull Collection<VirtualFile> getMergingRoots() {
    return getRootsFromRepositories(filter(GitRepositoryManager.getInstance(myProject).getRepositories(),
                                           repository -> repository.getState() == Repository.State.MERGING));
  }

  public void mergeCommit(@NotNull Collection<? extends VirtualFile> roots) throws VcsException {
    for (VirtualFile root : roots) {
      mergeCommit(root);
    }
  }

  public void mergeCommit(@NotNull VirtualFile root) throws VcsException {
    GitLineHandler handler = new GitLineHandler(myProject, root, GitCommand.COMMIT);
    handler.setStdoutSuppressed(false);

    GitRepository repository = GitUtil.getRepositoryForRoot(myProject, root);
    File messageFile = repository.getRepositoryFiles().getMergeMessageFile();
    if (!messageFile.exists()) {
      final GitBranch branch = repository.getCurrentBranch();
      final String branchName = branch != null ? branch.getName() : "";
      handler.addParameters("-m", "Merge branch '" + branchName + "' of " + root.getPresentableUrl() + " with conflicts.");
    } else {
      handler.addParameters("-F");
      handler.addAbsoluteFile(messageFile);
    }
    handler.endOptions();
    Git.getInstance().runCommand(handler).throwOnError();
  }

}
