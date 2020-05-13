// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.roots;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.roots.VcsIntegrationEnabler;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;

public final class GitIntegrationEnabler extends VcsIntegrationEnabler {
  private static final Logger LOG = Logger.getInstance(GitIntegrationEnabler.class);

  public GitIntegrationEnabler(@NotNull GitVcs vcs) {
    super(vcs);
  }

  @Override
  protected boolean initOrNotifyError(@NotNull final VirtualFile projectDir) {
    VcsNotifier vcsNotifier = VcsNotifier.getInstance(myProject);
    GitCommandResult result = Git.getInstance().init(myProject, projectDir);
    if (result.success()) {
      refreshVcsDir(projectDir, GitUtil.DOT_GIT);
      vcsNotifier.notifySuccess(GitBundle.message("git.integration.created.git.repository.in", projectDir.getPresentableUrl()));
      return true;
    }
    else {
      vcsNotifier.notifyError(GitBundle.message("git.integration.could.not.git.init", projectDir.getPresentableUrl()), result.getErrorOutputAsHtmlString(), true);
      LOG.info(result.getErrorOutputAsHtmlString());
      return false;
    }
  }
}
