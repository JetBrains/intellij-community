// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects the error "error: Cannot delete branch '<branch>' checked out at '<path>'" which occurs when
 * trying to delete a branch that is checked out in another worktree.
 */
public class GitBranchCheckedOutInWorktreeDeleteDetector implements GitLineEventDetector {
  // Git error message format: error: Cannot delete branch 'branch-name' checked out at '/path/to/worktree'
  private static final Pattern CANNOT_DELETE_CHECKED_OUT_PATTERN =
    Pattern.compile("error:\\s*Cannot delete branch\\s*'(.+)'\\s*checked out at\\s*'(.+)'");

  private boolean myDetected;
  private @Nullable String myBranchName;
  private @Nullable String myWorktreePath;

  @Override
  public void onLineAvailable(@NotNull String line, Key outputType) {
    Matcher matcher = CANNOT_DELETE_CHECKED_OUT_PATTERN.matcher(line);
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
