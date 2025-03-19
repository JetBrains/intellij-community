// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.idea.AppMode
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object CommandLineArgs {
  private const val SPLASH = "splash"
  private const val NO_SPLASH = "nosplash"

  fun isKnownArgument(arg: String): Boolean {
    return SPLASH == arg || NO_SPLASH == arg ||
           AppMode.DISABLE_NON_BUNDLED_PLUGINS.equals(arg, ignoreCase = true) || AppMode.DONT_REOPEN_PROJECTS.equals(arg, ignoreCase = true)
  }

  fun isSplashNeeded(args: List<String>): Boolean {
    for (arg in args) {
      if (SPLASH == arg) {
        return true
      }
      else if (NO_SPLASH == arg) {
        return false
      }
    }

    // products may specify `splash` VM property; `nosplash` is deprecated and should be checked first
    // BOTH properties maybe specified - so, we must check NO_SPLASH first, it allows user to disable splash even product specifies SPLASH,
    // as user cannot override SPLASH property if it is set by launcher
    return when {
      java.lang.Boolean.getBoolean(NO_SPLASH) -> false
      java.lang.Boolean.getBoolean(SPLASH) -> true
      else -> false
    }
  }
}