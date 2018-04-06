// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider.commit;

import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.CommitSession;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class HgCommitAndPushExecutor implements CommitExecutor {
  public static final String ID = "Hg.Commit.And.Push.Executor";

  private final HgCheckinEnvironment myCheckinEnvironment;

  public HgCommitAndPushExecutor(HgCheckinEnvironment checkinEnvironment) {
    myCheckinEnvironment = checkinEnvironment;
  }

  @Nls
  public String getActionText() {
    return "Commit and &Push...";
  }

  @Override
  public boolean useDefaultAction() {
    return false;
  }

  @Nullable
  @Override
  public String getId() {
    return ID;
  }

  @NotNull
  public CommitSession createCommitSession() {
    myCheckinEnvironment.setNextCommitIsPushed();
    return CommitSession.VCS_COMMIT;
  }
}
