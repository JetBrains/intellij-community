// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea;

import com.intellij.ide.plugins.PluginManagerCore;
import org.jetbrains.annotations.NotNull;

public class CommandLineArgs {
  public static final String DISABLE_NON_BUNDLED_PLUGINS = "disableNonBundledPlugins";
  @SuppressWarnings("SpellCheckingInspection")
  public static final String NO_SPLASH = "nosplash";

  public static void parse(String[] args) {
    for (String arg : args) {
      if (arg.equalsIgnoreCase(DISABLE_NON_BUNDLED_PLUGINS)) {
        PluginManagerCore.ourDisableNonBundledPlugins = true;
      }
    }
  }

  public static boolean isKnownArgument(@NotNull String arg) {
    return NO_SPLASH.equals(arg) || DISABLE_NON_BUNDLED_PLUGINS.equalsIgnoreCase(arg);
  }
}
