// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config;

import git4idea.i18n.GitBundle;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum GitRefNameValidatorReplacementOption {
  UNDERSCORE("settings.branching.name.validator.replacement.option.underscore", "_"),
  HYPHEN("settings.branching.name.validator.replacement.option.hyphen", "-"),
  EMPTY_CHARACTER("settings.branching.name.validator.replacement.option.empty.string", "");

  @NotNull
  private final String myTextKey;

  @NotNull
  @Pattern("\\S?")
  private final String myReplacementString;

  GitRefNameValidatorReplacementOption(
    @NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String textKey,
    @NotNull String replacementString
  ) {
    myTextKey = textKey;
    myReplacementString = replacementString;
  }

  @NotNull
  public static GitRefNameValidatorReplacementOption getDefault() {
    return HYPHEN;
  }

  @NotNull
  public String getReplacementString() {
    return this.myReplacementString;
  }

  @Nls
  @NotNull
  public String toString() {
    return GitBundle.message(this.myTextKey);
  }
}
