// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.impl.XLineBreakpointInstallationInfo
import com.intellij.xdebugger.impl.breakpoints.*
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeLineBreakpointProxy
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy.Companion.useFeProxy
import kotlinx.coroutines.*
import javax.swing.Icon

internal class FrontendXLightLineBreakpoint(
  override val project: Project,
  parentCs: CoroutineScope,
  override val type: XLineBreakpointTypeProxy,
  private val installationInfo: XLineBreakpointInstallationInfo,
  private val breakpointManager: XBreakpointManagerProxy,
) : XLightLineBreakpointProxy {
  private val cs = parentCs.childScope("FrontendXLightLineBreakpoint")

  private val visualRepresentation = XBreakpointVisualRepresentation(this, useFeProxy(), breakpointManager)

  init {
    // TODO IJPL-185322: let's add loading icon if light breakpoint is alive for more than ~300ms
    cs.launch(Dispatchers.EDT) {
      breakpointManager.getLineBreakpointManager().breakpointChanged(this@FrontendXLightLineBreakpoint)
    }
  }

  fun dispose() {
    cs.cancel()
    visualRepresentation.removeHighlighter()
    visualRepresentation.redrawInlineInlays(getFile(), getLine())
  }

  override fun isDisposed(): Boolean {
    return !cs.isActive
  }

  override fun getFile(): VirtualFile? {
    return installationInfo.position.file
  }

  // TODO IJPL-185322: line might be changed, when document is modified.
  //  Should we support it?
  override fun getLine(): Int {
    return installationInfo.position.line
  }

  // TODO IJPL-185322: should we support highlighting range to partially highlight the line?
  override fun getHighlightRange(): TextRange? {
    return null
  }

  override fun isEnabled(): Boolean {
    return true
  }

  override fun updateIcon() {
    // Do nothing for light breakpoint
  }

  override fun createGutterIconRenderer(): GutterIconRenderer? {
    return FrontendXLightBreakpointGutterIconRenderer(this)
  }

  override fun doUpdateUI(callOnUpdate: () -> Unit) {
    if (useFeLineBreakpointProxy()) {
      visualRepresentation.doUpdateUI(callOnUpdate)
    }
  }

  private class FrontendXLightBreakpointGutterIconRenderer(
    private val lightBreakpoint: FrontendXLightLineBreakpoint,
  ) : CommonBreakpointGutterIconRenderer() {

    override fun equals(obj: Any?): Boolean {
      return obj is FrontendXLightBreakpointGutterIconRenderer
             && lightBreakpoint == obj.lightBreakpoint
    }

    override fun hashCode(): Int {
      return lightBreakpoint.hashCode()
    }

    override fun getIcon(): Icon {
      return lightBreakpoint.type.enabledIcon
    }
  }
}