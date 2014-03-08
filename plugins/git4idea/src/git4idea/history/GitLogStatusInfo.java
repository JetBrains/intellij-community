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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Information about a single file change as returned by {@code git log status --name-status}.
 */
public class GitLogStatusInfo {
  
  private final GitChangeType myType;
  private final String myPath;
  private final String mySecondPath;

  GitLogStatusInfo(@NotNull GitChangeType type, @NotNull String path, @Nullable String secondPath) {
    mySecondPath = secondPath;
    myPath = path;
    myType = type;
  }

  @NotNull
  public String getFirstPath() {
    return myPath;
  }

  @NotNull
  public GitChangeType getType() {
    return myType;
  }

  @Nullable
  public String getSecondPath() {
    return mySecondPath;
  }

  @Override
  public String toString() {
    String s = myType.toString() + " " + myPath;
    if (mySecondPath != null) {
      s += " -> " + mySecondPath;
    }
    return s;
  }
}
