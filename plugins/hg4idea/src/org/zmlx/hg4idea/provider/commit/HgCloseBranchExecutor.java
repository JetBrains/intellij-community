// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider.commit;

import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutorBase;
import com.intellij.openapi.vcs.changes.CommitSession;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collection;

public class HgCloseBranchExecutor extends CommitExecutorBase {

  @NotNull private final HgCheckinEnvironment myCheckinEnvironment;
  @NotNull private static final String CLOSE_BRANCH_TITLE = "Commit And Close";


  public HgCloseBranchExecutor(@NotNull HgCheckinEnvironment environment) {
    myCheckinEnvironment = environment;
  }

  @Override
  public boolean areChangesRequired() {
    return false;
  }

  @NotNull
  @Nls
  @Override
  public String getActionText() {
    return CLOSE_BRANCH_TITLE;
  }

  @NotNull
  @Override
  public CommitSession createCommitSession(@NotNull CommitContext commitContext) {
    myCheckinEnvironment.setCloseBranch(true);
    return CommitSession.VCS_COMMIT;
  }

  public void setRepositories(@NotNull Collection<HgRepository> repositories) {
    myCheckinEnvironment.setRepos(repositories);
  }
}
