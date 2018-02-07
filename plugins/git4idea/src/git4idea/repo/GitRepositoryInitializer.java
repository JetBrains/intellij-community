// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo;

import com.intellij.openapi.project.ProjectManager;
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
    // TODO remove the fake project instance when GitHandler knows how to run without project
    GitLineHandler handler = new GitLineHandler(ProjectManager.getInstance().getDefaultProject(), rootDir, GitCommand.INIT);
    Git.getInstance().runCommand(handler).getOutputOrThrow();
  }

  @NotNull
  @Override
  public VcsKey getSupportedVcs() {
    return GitVcs.getKey();
  }
}
