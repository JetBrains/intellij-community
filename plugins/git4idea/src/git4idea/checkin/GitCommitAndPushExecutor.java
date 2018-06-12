// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkin;

import com.intellij.openapi.vcs.changes.CommitExecutor;
import com.intellij.openapi.vcs.changes.CommitSession;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class GitCommitAndPushExecutor implements CommitExecutor {
  public static final String ID = "Git.Commit.And.Push.Executor";

  @NotNull private final GitCheckinEnvironment myCheckinEnvironment;

  public GitCommitAndPushExecutor(@NotNull GitCheckinEnvironment checkinEnvironment) {
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

  @Override
  public boolean supportsPartialCommit() {
    return true;
  }

  @NotNull
  public CommitSession createCommitSession() {
    myCheckinEnvironment.setNextCommitIsPushed(true);
    return CommitSession.VCS_COMMIT;
  }
}
