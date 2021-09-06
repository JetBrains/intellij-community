package com.intellij.xdebugger.impl.frame

import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.XDebugSessionTabCustomizer
import javax.swing.JComponent

class XSplitterWatchesViewImpl(
  session: XDebugSessionImpl,
  watchesInVariables: Boolean,
  isVertical: Boolean,
  withToolbar: Boolean
) : XWatchesViewImpl(session, watchesInVariables, isVertical, withToolbar), DnDNativeTarget, XWatchesView {

  companion object {
    const val proportionKey = "debugger.immediate.window.in.watches.proportion.key"
  }

  override fun constructPanel(localsPanelComponent: JComponent): BorderLayoutPanel {
    val session = mySession.get() ?: throw IllegalStateException("Not null session is expected here")
    val bottomLocalsComponentProvider = (session.debugProcess as? XDebugSessionTabCustomizer)?.bottomLocalsComponentProvider
                                        ?: throw IllegalStateException("BottomLocalsComponentProvider is not implemented to use SplitterWatchesVariablesView")

    val evaluatorComponent = bottomLocalsComponentProvider.createBottomLocalsComponent()
    val splitter = OnePixelSplitter(true, proportionKey, 0.01f, 0.99f)

    splitter.firstComponent = localsPanelComponent

    if (PropertiesComponent.getInstance().getBoolean("debugger.immediate.window.in.watches", true))
      splitter.secondComponent = evaluatorComponent

    splitter.dividerWidth = 1
    splitter.divider.background = UIUtil.CONTRAST_BORDER_COLOR

    return BorderLayoutPanel().apply { add(splitter) }
  }
}