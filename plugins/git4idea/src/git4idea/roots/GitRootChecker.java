// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.repo.GitModulesFileReader;
import git4idea.repo.GitRepositoryFiles;
import git4idea.repo.GitSubmoduleInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;

final class GitRootChecker extends VcsRootChecker {
  @Override
  public boolean isRoot(@NotNull VirtualFile path) {
    return path.findChild(GitUtil.DOT_GIT) != null && // fast check without refreshing the VFS
           GitUtil.isGitRoot(path.toNioPath());
  }

  @Override
  public boolean validateRoot(@NotNull VirtualFile file) {
    return GitUtil.isGitRoot(file.toNioPath());
  }

  @Override
  public @NotNull VcsKey getSupportedVcs() {
    return GitVcs.getKey();
  }

  @Override
  public boolean isVcsDir(@NotNull String dirName) {
    return dirName.equalsIgnoreCase(GitUtil.DOT_GIT);
  }

  @Override
  public boolean isIgnored(@NotNull VirtualFile root, @NotNull VirtualFile checkForIgnore) {
    // check-ignore was introduced in 1.8.2,
    // executing the command for older Gits will fail with exit code 1, which we'll treat as "not ignored" for simplicity
    GitLineHandler handler = new GitLineHandler(null, root, GitCommand.CHECK_IGNORE);
    handler.addParameters("--quiet"); // Don't output anything, just set exit status: 0 if ignored
    handler.addRelativeFiles(Collections.singletonList(checkForIgnore));
    return Git.getInstance().runCommand(handler).success();
  }

  @Override
  public @NotNull List<VirtualFile> suggestDependentRoots(@NotNull VirtualFile vcsRoot) {
    try {
      Path submoduleFile = vcsRoot.toNioPath().resolve(GitRepositoryFiles.SUBMODULES_FILE);
      Collection<GitSubmoduleInfo> submoduleInfos = new GitModulesFileReader().read(submoduleFile);

      return ContainerUtil.mapNotNull(submoduleInfos, info -> {
        return VcsUtil.getFilePath(vcsRoot, info.getPath()).getVirtualFile();
      });
    }
    catch (CancellationException e) {
      throw e;
    }
    catch (Exception e) {
      Logger.getInstance(GitRootChecker.class).warn(e);
      return Collections.emptyList();
    }
  }
}
