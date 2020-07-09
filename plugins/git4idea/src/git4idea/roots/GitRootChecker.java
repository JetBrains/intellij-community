// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.roots;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

final class GitRootChecker extends VcsRootChecker {
  @Override
  public boolean isRoot(@NotNull String path) {
    return GitUtil.isGitRoot(path);
  }

  @Override
  @NotNull
  public VcsKey getSupportedVcs() {
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
    GitLineHandler handler = new GitLineHandler(null, virtualToIoFile(root), GitCommand.CHECK_IGNORE);
    handler.addParameters("--quiet"); // Don't output anything, just set exit status: 0 if ignored
    handler.addRelativeFiles(Collections.singletonList(checkForIgnore));
    return Git.getInstance().runCommand(handler).success();
  }
}
