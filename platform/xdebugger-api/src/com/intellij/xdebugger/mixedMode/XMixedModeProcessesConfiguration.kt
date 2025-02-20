// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.mixedMode

import org.jetbrains.annotations.ApiStatus

/**
 * Container for flags that resolve cases when both debuggers can provide the same functionality, and we have to choose between them
 */
@ApiStatus.Internal
data class XMixedModeProcessesConfiguration(val useLowDebugProcessConsole: Boolean, val useLowDebugProcessDetachBehavior : Boolean)
