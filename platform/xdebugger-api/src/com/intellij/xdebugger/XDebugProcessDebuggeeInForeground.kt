// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XDebugProcessDebuggeeInForeground {
  fun bringToForeground() : Boolean
  fun isBringingToForegroundApplicable() : Boolean
}