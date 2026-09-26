// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Describes custom policies that can prohibit breakpoints in a [VirtualFile].
 */
@ApiStatus.Internal
fun interface BreakpointFileProhibitionPolicy {

  companion object {
    private val EP_NAME = ExtensionPointName.create<BreakpointFileProhibitionPolicy>("com.intellij.xdebugger.breakpointFileProhibitionPolicy")

    fun isBreakpointProhibited(virtualFile: VirtualFile): Boolean =
      EP_NAME.computeSafeIfAny { it.isBreakpointProhibited(virtualFile) } ?: false

  }

  fun isBreakpointProhibited(virtualFile: VirtualFile): Boolean
}
