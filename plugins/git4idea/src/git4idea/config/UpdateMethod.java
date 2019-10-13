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
package git4idea.config;

import org.jetbrains.annotations.NotNull;

/**
 * The type of update to perform
 */
public enum UpdateMethod {
  /**
   * Use default specified in the config file for the branch
   */
  BRANCH_DEFAULT("Branch Default", "Branch Default"),
  /**
   * Merge fetched commits with local branch
   */
  MERGE("Merge", "Merge the incoming changes into the current branch"),
  /**
   * Rebase local commits upon the fetched branch
   */
  REBASE("Rebase", "Rebase the current branch on top of the incoming changes");

  @NotNull private final String myName;
  @NotNull private final String myPresentation;

  UpdateMethod(@NotNull String name, @NotNull String presentation) {
    myName = name;
    myPresentation = presentation;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getPresentation() {
    return myPresentation;
  }
}
