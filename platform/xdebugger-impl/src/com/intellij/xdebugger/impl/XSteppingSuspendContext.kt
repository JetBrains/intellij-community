// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.annotations.ApiStatus

/**
 * This suspend-context represents a situation of stepping with another thread suspended also.
 * In this case we should show the UI as resumed but make it available to switch the thread.
 */
@ApiStatus.Internal
abstract class XSteppingSuspendContext : XSuspendContext()
