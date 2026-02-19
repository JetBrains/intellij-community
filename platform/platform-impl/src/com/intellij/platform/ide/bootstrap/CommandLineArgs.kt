// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.idea.ApplicationStartArguments
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object CommandLineArgs {
  private const val SPLASH = "splash"
  private const val NO_SPLASH = "nosplash"

  fun isSplashNeeded(args: List<String>): Boolean {
    if (ApplicationStartArguments.SPLASH.isSet(args)) return true
    if (ApplicationStartArguments.NO_SPLASH.isSet(args)) return false

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