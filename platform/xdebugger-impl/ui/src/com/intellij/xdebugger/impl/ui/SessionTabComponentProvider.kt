package com.intellij.xdebugger.impl.ui

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent


interface SessionTabComponentProvider {
  @Deprecated("Use createBottomLocalsComponent(session: XDebugSessionProxy)")
  fun createBottomLocalsComponent(): JComponent;

  @ApiStatus.Internal
  fun createBottomLocalsComponent(session: XDebugSessionProxy): JComponent

  @ApiStatus.Internal
  companion object {
    private val EP_NAME = ExtensionPointName<SessionTabComponentProvider>("com.intellij.xdebugger.debuggerTabCustomizer")

    fun hasProvider(): Boolean = EP_NAME.extensionList.any()

    fun getInstance(): SessionTabComponentProvider {
      assert(hasProvider()) { "SessionTabComponentProvider is not registered" }
      return EP_NAME.extensionList.single()
    }
  }
}