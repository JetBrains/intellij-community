package com.intellij.xdebugger.impl.ui

import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter
import com.intellij.xdebugger.XDebugProcess
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
@ApiStatus.Experimental
interface XDebugSessionTabCustomizer {
  fun getBottomLocalsComponentProvider(): SessionTabComponentProvider? = null

  fun allowFramesViewCustomization(): Boolean = false

  fun getDefaultFramesViewKey(): String? = null

  fun forceShowNewDebuggerUi(): Boolean = false
}

fun XDebugProcess.allowFramesViewCustomization(): Boolean {
  return (this as? XDebugSessionTabCustomizer)?.allowFramesViewCustomization() ?: false
}

@Deprecated("Use getSessionTabCustomer().getBottomLocalsComponentProvider(). If you need to find a session proxy, use XDebugManagerProxy, XDebuggerEntityConverter.asProxy")
fun XDebugProcess.getBottomLocalsComponentProvider(): SessionTabComponentProvider? {
  val newProvider = (this as? XDebugSessionTabCustomizer)?.getBottomLocalsComponentProvider() ?: return null
  return object: SessionTabComponentProvider {
    @Deprecated("Use createBottomLocalsComponent(session: XDebugSessionProxy)")
    override fun createBottomLocalsComponent(): JComponent {
      val session = this@getBottomLocalsComponentProvider.session
      val proxy = XDebuggerEntityConverter.asProxy(session)
      return newProvider.createBottomLocalsComponent(proxy)
    }

    override fun createBottomLocalsComponent(session: XDebugSessionProxy): JComponent {
      error("Should be unreachable")
    }
  }
}

@ApiStatus.Internal
fun XDebugProcess.useSplitterView(): Boolean = getSessionTabCustomer()?.getBottomLocalsComponentProvider() != null


fun XDebugProcess.forceShowNewDebuggerUi(): Boolean {
  return (this as? XDebugSessionTabCustomizer)?.forceShowNewDebuggerUi() ?: false
}

@ApiStatus.Internal
fun XDebugProcess.getDefaultFramesViewKey(): String? {
  return (this as? XDebugSessionTabCustomizer)?.getDefaultFramesViewKey()
}

@ApiStatus.Internal
fun XDebugProcess.getSessionTabCustomer(): XDebugSessionTabCustomizer? {
  return this as? XDebugSessionTabCustomizer
}