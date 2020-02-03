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

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static git4idea.i18n.GitBundle.message;

/**
 * The type of update to perform
 */
public enum UpdateMethod {
  /**
   * Use default specified in the config file for the branch
   */
  BRANCH_DEFAULT(message("settings.git.update.method.branch.default"), message("settings.git.update.method.branch.default")),
  /**
   * Merge fetched commits with local branch
   */
  MERGE(message("settings.git.update.method.merge"), message("settings.git.update.method.merge.description")),
  /**
   * Rebase local commits upon the fetched branch
   */
  REBASE(message("settings.git.update.method.rebase"), message("settings.git.update.method.rebase.description"));

  @NotNull private final String myName;
  @NotNull private final String myPresentation;

  UpdateMethod(@NotNull @Nls String name, @NotNull @Nls String presentation) {
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
