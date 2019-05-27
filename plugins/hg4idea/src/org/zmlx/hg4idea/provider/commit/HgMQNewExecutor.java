// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider.commit;

import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.CommitSession;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class HgMQNewExecutor implements CommitExecutor {
  //todo:should be moved to create patch dialog as an EP -> create patch with...  MQ
  @NotNull private final HgCheckinEnvironment myCheckinEnvironment;

  public HgMQNewExecutor(@NotNull HgCheckinEnvironment checkinEnvironment) {
    myCheckinEnvironment = checkinEnvironment;
  }

  @NotNull
  @Nls
  @Override
  public String getActionText() {
    return "Create M&Q Patch";
  }

  @NotNull
  @Override
  public CommitSession createCommitSession(@NotNull CommitContext commitContext) {
    myCheckinEnvironment.setMqNew();
    return CommitSession.VCS_COMMIT;
  }
}
