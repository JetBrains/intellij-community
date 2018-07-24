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
package git4idea.roots;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsRootChecker;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.config.GitExecutableManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static com.intellij.util.containers.ContainerUtil.emptyList;

/**
 * @author Kirill Likhodedov
 */
public class GitRootChecker extends VcsRootChecker {

  @Override
  public boolean isRoot(@NotNull String path) {
    return GitUtil.isGitRoot(new File(path));
  }

  @Override
  @NotNull
  public VcsKey getSupportedVcs() {
    return GitVcs.getKey();
  }

  @Override
  public boolean isVcsDir(@NotNull String path) {
    return path.toLowerCase().endsWith(GitUtil.DOT_GIT);
  }

  @Override
  public boolean isIgnored(@NotNull VirtualFile root, @NotNull VirtualFile checkForIgnore) {
    // check-ignore was introduced in 1.8.2,
    // executing the command for older Gits will fail with exit code 1, which we'll treat as "not ignored" for simplicity
    GitLineHandler handler = new GitLineHandler(null, virtualToIoFile(root),
                                                GitExecutableManager.getInstance().getPathToGit(), GitCommand.CHECK_IGNORE, emptyList());
    handler.addParameters("--quiet"); // Don't output anything, just set exit status: 0 if ignored
    handler.addParameters(checkForIgnore.getPath());
    return Git.getInstance().runCommand(handler).success();
  }
}
