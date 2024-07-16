// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.breakpoints

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Interface for classes that could introduce custom policies for inline breakpoints disabling.
 * @see com.intellij.xdebugger.XDebuggerUtil.areInlineBreakpointsEnabled
 */
@ApiStatus.Experimental
interface InlineBreakpointsDisabler {

  companion object {
    val EP: ExtensionPointName<InlineBreakpointsDisabler> = ExtensionPointName.create(
      "com.intellij.xdebugger.inlineBreakpointsDisabler")
  }

  /**
   * Determines whether inline breakpoints should be disabled in a given [VirtualFile].
   */
  fun areInlineBreakpointsDisabled(virtualFile: VirtualFile?) : Boolean
}