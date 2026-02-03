// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public enum GitRebaseResumeMode {
  CONTINUE("--continue"),
  SKIP("--skip");

  private final @NotNull String myCommandLineArgument;

  GitRebaseResumeMode(@NotNull @NonNls String argument) {
    myCommandLineArgument = argument;
  }

  public @NonNls @NotNull String asCommandLineArgument() {
    return myCommandLineArgument;
  }
}
