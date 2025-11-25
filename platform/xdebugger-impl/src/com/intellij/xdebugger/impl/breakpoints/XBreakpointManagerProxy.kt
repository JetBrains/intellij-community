// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.XLineBreakpointInstallationInfo
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface XBreakpointManagerProxy {
  val breakpointsDialogSettings: XBreakpointsDialogState?

  val dependentBreakpointManager: XDependentBreakpointManagerProxy
  val inlineBreakpointsCache: InlineBreakpointsCache

  fun setBreakpointsDialogSettings(settings: XBreakpointsDialogState)

  fun getDefaultGroup(): String?

  fun setDefaultGroup(group: String?)

  suspend fun awaitBreakpointCreation(breakpointId: XBreakpointId): XBreakpointProxy?

  fun getAllBreakpointItems(): List<BreakpointItem>
  fun getAllBreakpoints(): List<XBreakpointProxy>

  fun getLineBreakpointManager(): XLineBreakpointManager

  fun getAllBreakpointTypes(): List<XBreakpointTypeProxy>
  fun getLineBreakpointTypes(): List<XLineBreakpointTypeProxy>

  fun subscribeOnBreakpointsChanges(disposable: Disposable, listener: () -> Unit)
  fun getLastRemovedBreakpoint(): XBreakpointProxy?

  fun removeBreakpoint(breakpoint: XBreakpointProxy)

  fun rememberRemovedBreakpoint(breakpoint: XBreakpointProxy)
  fun restoreRemovedBreakpoint(breakpoint: XBreakpointProxy)

  fun copyLineBreakpoint(breakpoint: XLineBreakpointProxy, file: VirtualFile, line: Int)

  fun findBreakpointAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): XLineBreakpointProxy? =
    findBreakpointsAtLine(type, file, line).firstOrNull()

  fun findBreakpointsAtLine(type: XLineBreakpointTypeProxy, file: VirtualFile, line: Int): List<XLineBreakpointProxy>

  suspend fun <T> withLightBreakpointIfPossible(editor: Editor?, info: XLineBreakpointInstallationInfo, block: suspend () -> T): T
}
