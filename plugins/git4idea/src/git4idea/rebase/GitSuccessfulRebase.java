// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase;

import com.intellij.openapi.util.text.StringUtil;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

class GitSuccessfulRebase extends GitRebaseStatus {
  private final SuccessType mySuccessType;

  private GitSuccessfulRebase(@NotNull SuccessType successType, @NotNull Collection<GitRebaseUtils.CommitInfo> skippedCommits) {
    super(Type.SUCCESS, skippedCommits);
    mySuccessType = successType;
  }

  @NotNull
  public SuccessType getSuccessType() {
    return mySuccessType;
  }

  @NotNull
  static GitSuccessfulRebase parseFromOutput(@NotNull List<String> output, @NotNull Collection<GitRebaseUtils.CommitInfo> skippedCommits) {
    return new GitSuccessfulRebase(SuccessType.fromOutput(output), skippedCommits);
  }

  enum SuccessType {
    REBASED {
      @NotNull
      @Override
      public String formatMessage(@Nullable String currentBranch, @Nullable String baseBranch, boolean withCheckout) {
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
    },
    UP_TO_DATE {
      @NotNull
      @Override
      public String formatMessage(@Nullable String currentBranch, @Nullable String baseBranch, boolean withCheckout) {
        return GitBundle.message(
          "rebase.notification.successful.up.to.date.message",
          convertBooleanToInt(currentBranch != null), currentBranch,
          convertBooleanToInt(baseBranch != null), baseBranch);
      }
    },
    FAST_FORWARDED {
      @NotNull
      @Override
      public String formatMessage(@Nullable String currentBranch, @Nullable String baseBranch, boolean withCheckout) {
        if (withCheckout) {
          return GitBundle.message(
            "rebase.notification.successful.fast.forwarded.checkout.message",
            convertBooleanToInt(currentBranch != null), currentBranch,
            convertBooleanToInt(baseBranch != null), baseBranch);
        }
        else {
          return GitBundle.message(
            "rebase.notification.successful.fast.forwarded.message",
            convertBooleanToInt(currentBranch != null), currentBranch,
            convertBooleanToInt(baseBranch != null), baseBranch);
        }
      }
    };

    @NotNull
    abstract String formatMessage(@Nullable String currentBranch, @Nullable String baseBranch, boolean withCheckout);

    @NotNull
    public static SuccessType fromOutput(@NotNull List<String> output) {
      for (String line : output) {
        if (StringUtil.containsIgnoreCase(line, "Fast-forwarded")) {
          return FAST_FORWARDED;
        }
        if (StringUtil.containsIgnoreCase(line, "is up to date")) {
          return UP_TO_DATE;
        }
      }
      return REBASED;
    }

    private static int convertBooleanToInt(boolean expression) {
      return expression ? 1 : 0;
    }
  }
}
