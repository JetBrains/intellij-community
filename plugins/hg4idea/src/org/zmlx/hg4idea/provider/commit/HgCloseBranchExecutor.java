/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.provider.commit;

import com.intellij.openapi.vcs.changes.CommitExecutorBase;
import com.intellij.openapi.vcs.changes.CommitSession;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.Collection;

public class HgCloseBranchExecutor extends CommitExecutorBase {

  @NotNull private final HgCheckinEnvironment myCheckinEnvironment;
  @NotNull private static final String CLOSE_BRANCH_TITLE = "Commit And &Close" ;


  public HgCloseBranchExecutor(@NotNull HgCheckinEnvironment environment) {
    myCheckinEnvironment = environment;
  }

  @Override
  public boolean areChangesRequired() {
    return false;
  }

  @Nls
  @Override
  public String getActionText() {
    return CLOSE_BRANCH_TITLE;
  }

  @NotNull
  @Override
  public CommitSession createCommitSession() {
    myCheckinEnvironment.setCloseBranch(true);
    return CommitSession.VCS_COMMIT;
  }

  public void setRepositories(@NotNull Collection<HgRepository> repositories) {
    myCheckinEnvironment.setRepos(repositories);
  }
}
