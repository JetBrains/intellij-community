// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea;

import org.jetbrains.annotations.NotNull;

public final class CommandLineArgs {
  public static final String SPLASH = "splash";
  @SuppressWarnings("SpellCheckingInspection")
  public static final String NO_SPLASH = "nosplash";

  public static boolean isKnownArgument(@NotNull String arg) {
    return SPLASH.equals(arg) || NO_SPLASH.equals(arg) ||
           AppMode.DISABLE_NON_BUNDLED_PLUGINS.equalsIgnoreCase(arg) || AppMode.DONT_REOPEN_PROJECTS.equalsIgnoreCase(arg);
  }
}
