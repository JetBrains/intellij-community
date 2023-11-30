// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.vcs.VcsRepositoryInitializer;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class GitRepositoryInitializer implements VcsRepositoryInitializer {

  @Override
  public void initRepository(@NotNull File rootDir) throws VcsException {
    GitLineHandler handler = new GitLineHandler(null, rootDir, GitCommand.INIT);
    Git.getInstance().runCommand(handler).throwOnError();
  }

  @Override
  public @NotNull VcsKey getSupportedVcs() {
    return GitVcs.getKey();
  }
}
