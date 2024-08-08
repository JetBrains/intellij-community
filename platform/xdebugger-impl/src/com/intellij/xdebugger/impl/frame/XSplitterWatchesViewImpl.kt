// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.getBottomLocalsComponentProvider
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Allows customizing of variables view and splitting into 2 components.
 * Notice that you must provide the bottom component of the view by implementing XDebugSessionTabCustomizer in your XDebugProcess
 * @see com.intellij.xdebugger.impl.ui.XDebugSessionTabCustomizer.getBottomLocalsComponentProvider
 */
@Internal
class XSplitterWatchesViewImpl(
  session: XDebugSessionImpl,
  watchesInVariables: Boolean,
  isVertical: Boolean,
  withToolbar: Boolean
) : XWatchesViewImpl(session.also { checkContract(it) }, watchesInVariables, isVertical, withToolbar), DnDNativeTarget, XWatchesView {
  companion object {
    private const val proportionKey = "debugger.immediate.window.in.watches.proportion.key"

    private fun checkContract(session: XDebugSessionImpl) {
      if (tryGetBottomComponentProvider(session) == null)
        error("XDebugProcess must provide a properly configured XDebugSessionTabCustomizer to use XSplitterWatchesViewImpl. Read JavaDoc for details")
    }

    private fun tryGetBottomComponentProvider(session: XDebugSessionImpl) = session.debugProcess.getBottomLocalsComponentProvider()
  }

  lateinit var splitter: OnePixelSplitter
    private set

  override fun createMainPanel(localsPanelComponent: JComponent): JPanel {
    val session = mySession.get() ?: error("Not null session is expected here")
    val bottomLocalsComponentProvider = tryGetBottomComponentProvider(session)
                                        ?: error("BottomLocalsComponentProvider is not implemented to use SplitterWatchesVariablesView")

    val evaluatorComponent = bottomLocalsComponentProvider.createBottomLocalsComponent()
    splitter = OnePixelSplitter(true, proportionKey, 0.01f, 0.99f)

    splitter.firstComponent = localsPanelComponent
    splitter.secondComponent = evaluatorComponent

    return BorderLayoutPanel().addToCenter(splitter)
  }
}