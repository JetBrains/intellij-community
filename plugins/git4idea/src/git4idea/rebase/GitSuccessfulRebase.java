// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class GitSuccessfulRebase extends GitRebaseStatus {
  GitSuccessfulRebase() {
    super(Type.SUCCESS);
  }

  public static @NotNull @Nls String formatMessage(@Nullable String currentBranch, @Nullable String baseBranch, boolean withCheckout) {
    if (withCheckout) {
      return GitBundle.message(
        "rebase.notification.successful.rebased.checkout.message",
        convertBooleanToInt(currentBranch != null), currentBranch,
        convertBooleanToInt(baseBranch != null), baseBranch);
    }
    else {
      return GitBundle.message(
        "rebase.notification.successful.rebased.message",
        convertBooleanToInt(currentBranch != null), currentBranch,
        convertBooleanToInt(baseBranch != null), baseBranch);
    }
  }

  private static int convertBooleanToInt(boolean expression) {
    return expression ? 1 : 0;
  }
}
