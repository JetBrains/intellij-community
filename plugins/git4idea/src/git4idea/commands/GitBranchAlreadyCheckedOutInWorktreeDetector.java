// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the error "fatal: '<branch>' is already checked out at '<path>'" which occurs when
 * trying to checkout a branch that is already checked out in another worktree.
 */
public class GitBranchAlreadyCheckedOutInWorktreeDetector implements GitLineEventDetector {
  // Git error message format: fatal: '<branch>' is already checked out at '<path>'
  private static final Pattern ALREADY_CHECKED_OUT_PATTERN =
    Pattern.compile("fatal:\\s*'(.+)'\\s+is already checked out at\\s+'(.+)'");

  private boolean myDetected;
  private @Nullable String myBranchName;
  private @Nullable String myWorktreePath;

  @Override
  public void onLineAvailable(@NotNull String line, Key outputType) {
    Matcher matcher = ALREADY_CHECKED_OUT_PATTERN.matcher(line);
    if (matcher.find()) {
      myDetected = true;
      myBranchName = matcher.group(1);
      myWorktreePath = matcher.group(2);
    }
  }

  @Override
  public boolean isDetected() {
    return myDetected;
  }

  public @Nullable String getBranchName() {
    return myBranchName;
  }

  public @Nullable String getWorktreePath() {
    return myWorktreePath;
  }
}
