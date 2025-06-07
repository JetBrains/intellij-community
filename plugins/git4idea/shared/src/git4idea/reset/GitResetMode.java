// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.reset;

import com.intellij.openapi.util.NlsContexts;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum GitResetMode {

  SOFT("git.reset.mode.soft", "--soft", "git.reset.mode.soft.description"),
  MIXED("git.reset.mode.mixed", "--mixed", "git.reset.mode.mixed.description"),
  HARD("git.reset.mode.hard", "--hard", "git.reset.mode.hard.description"),
  KEEP("git.reset.mode.keep", "--keep", "git.reset.mode.keep.description");

  private final @NotNull String myName;
  private final @NotNull String myArgument;
  private final @NotNull String myDescription;

  GitResetMode(@NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String name,
               @NotNull @NonNls String argument,
               @NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String description) {
    myName = name;
    myArgument = argument;
    myDescription = description;
  }

  public static @NotNull GitResetMode getDefault() {
    return MIXED;
  }

  public @NotNull @NlsContexts.RadioButton String getName() {
    return GitBundle.message(myName);
  }

  public @NotNull String getArgument() {
    return myArgument;
  }

  public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
    return GitBundle.message(myDescription);
  }
}
