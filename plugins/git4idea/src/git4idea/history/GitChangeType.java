/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.history;

import git4idea.GitFormatException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
enum GitChangeType {
  MODIFIED('M'),
  ADDED('A'),
  COPIED('C'),
  DELETED('D'),
  RENAMED('R'),
  UNRESOLVED('U'),
  TYPE_CHANGED('T')
  ;

  private final char myChar;

  GitChangeType(char c) {
    myChar = c;
  }

  /**
   * Finds the GitChangeType by the given string returned by Git.
   * @throws GitFormatException if such status can't be found: it means either a developer mistake missing a possible valid status,
   * or a Git invalid output.
   */
  @NotNull
  static GitChangeType fromString(@NotNull String statusString) {
    assert statusString.length() > 0;
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
