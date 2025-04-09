// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * The type of update to perform
 */
public enum UpdateMethod {
  /**
   * Use default specified in the config file for the branch
   */
  BRANCH_DEFAULT("settings.git.update.method.branch.default",
                 "settings.git.update.method.branch.default"),
  /**
   * Merge fetched commits with local branch
   */
  MERGE("settings.git.update.method.merge",
        "settings.git.update.method.merge.description"),
  /**
   * Rebase local commits upon the fetched branch
   */
  REBASE("settings.git.update.method.rebase",
         "settings.git.update.method.rebase.description");

  private final @NotNull String myName;
  private final @NotNull String myPresentation;

  UpdateMethod(@NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String name,
               @NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String presentation) {
    myName = name;
    myPresentation = presentation;
  }

  public @NotNull @Nls String getMethodName() {
    return GitBundle.message(myName);
  }

  public @NotNull @Nls String getPresentation() {
    return GitBundle.message(myPresentation);
  }
}
