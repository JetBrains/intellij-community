// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history;

import git4idea.GitFormatException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public enum GitChangeType {
  MODIFIED('M'),
  ADDED('A'),
  COPIED('C'),
  DELETED('D'),
  RENAMED('R'),
  UNRESOLVED('U'),
  TYPE_CHANGED('T');

  private final char myChar;

  GitChangeType(char c) {
    myChar = c;
  }

  /**
   * Finds the GitChangeType by the given string returned by Git.
   *
   * @throws GitFormatException if such status can't be found: it means either a developer mistake missing a possible valid status,
   *                            or a Git invalid output.
   */
  static @NotNull GitChangeType fromString(@NotNull String statusString) {
    assert !statusString.isEmpty();
    char c = statusString.charAt(0);
    for (GitChangeType changeType : values()) {
      if (changeType.myChar == c) {
        return changeType;
      }
    }
    throw new GitFormatException("Unexpected status [" + statusString + "]");
  }

  @Override
  public String toString() {
    return String.valueOf(Character.toUpperCase(myChar));
  }
}
