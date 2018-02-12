/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.rebase;

import com.intellij.openapi.util.text.StringUtil;
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
        String msg;
        if (withCheckout) {
          msg = "Checked out" + mention(currentBranch) + " and rebased it";
        }
        else {
          msg = "Rebased" + mention(currentBranch);
        }
        if (baseBranch != null) msg += " on " + baseBranch;
        return msg;
      }
    },
    UP_TO_DATE {
      @NotNull
      @Override
      public String formatMessage(@Nullable String currentBranch, @Nullable String baseBranch, boolean withCheckout) {
        String msg = currentBranch != null ? currentBranch + " is up-to-date" : "Up-to-date";
        if (baseBranch != null) msg += " with " + baseBranch;
        return msg;
      }
    },
    FAST_FORWARDED {
      @NotNull
      @Override
      public String formatMessage(@Nullable String currentBranch, @Nullable String baseBranch, boolean withCheckout) {
        String msg;
        if (withCheckout) {
          msg = "Checked out" + mention(currentBranch) + " and fast-forwarded it";
        }
        else {
          msg = "Fast-forwarded" + mention(currentBranch);
        }
        if (baseBranch != null) msg += " to " + baseBranch;
        return msg;
      }
    };

    @NotNull
    private static String mention(@Nullable String currentBranch) {
      return currentBranch != null ? " " + currentBranch : "";
    }

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
  }
}
