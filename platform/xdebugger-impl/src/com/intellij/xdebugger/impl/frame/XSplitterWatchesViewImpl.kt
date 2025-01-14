// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.getBottomLocalsComponentProvider
import com.intellij.xdebugger.impl.ui.useSplitterView
import com.intellij.xdebugger.mixedMode.XMixedModeLowLevelDebugProcess
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

  private var myPanel: BorderLayoutPanel? = null
  private var customized = true
  private var localsPanel : JComponent? = null

  override fun createMainPanel(localsPanelComponent: JComponent): JPanel {
    // Can't initialize the panel in the constructor because this function is called from a super constructor
    customized = getShowCustomized()
    localsPanel = localsPanelComponent

    addMixedModeListenerIfNeeded(checkNotNull(session))
    return BorderLayoutPanel().also {
      myPanel = it
      updateMainPanel()
    }
  }

  private fun updateMainPanel() {
    val myPanel = requireNotNull(myPanel)
    val localsPanel = requireNotNull(localsPanel)
    myPanel.removeAll()

    if (!customized) {
      val wrappedLocalsPanel = BorderLayoutPanel().addToCenter(localsPanel) // have to wrap it because it's mutated in the super method
      myPanel.addToCenter(super.createMainPanel(wrappedLocalsPanel))
      return
    }

    val session = mySession.get() ?: error("Not null session is expected here")
    val bottomLocalsComponentProvider = tryGetBottomComponentProvider(session)
                                        ?: error("BottomLocalsComponentProvider is not implemented to use SplitterWatchesVariablesView")

    val evaluatorComponent = bottomLocalsComponentProvider.createBottomLocalsComponent()
    splitter = OnePixelSplitter(true, proportionKey, 0.01f, 0.99f)

    splitter.firstComponent = localsPanel
    splitter.secondComponent = evaluatorComponent

    myPanel.addToCenter(splitter)
  }

  private fun addMixedModeListenerIfNeeded(session: XDebugSessionImpl) {
    if (!session.isMixedMode) return

    val lowSupportsCustomization = session.getDebugProcess(true).useSplitterView()
    val highSupportsCustomization = session.getDebugProcess(false).useSplitterView()
    if (lowSupportsCustomization == highSupportsCustomization) return

    session.addSessionListener(object : XDebugSessionListener {
      override fun stackFrameChanged() {
        val showCustomizedView = getShowCustomized()
        if (customized == showCustomizedView) return
        customized = showCustomizedView
        updateMainPanel()
      }
    })
  }

  private fun getShowCustomized(): Boolean {
    val session = session ?: return false
    if (!session.isMixedMode) return true
    val frame = session.currentStackFrame ?: return false

    val low = session.getDebugProcess(true) as XMixedModeLowLevelDebugProcess
    val debugProcess = session.getDebugProcess(true)
    val lowSupportsCustomization = debugProcess.useSplitterView()
    val highSupportsCustomization = session.getDebugProcess(false).useSplitterView()

    val isLowFrame = low.belongsToMe(frame)
    val isHighFrame = !isLowFrame
    return isLowFrame && lowSupportsCustomization || isHighFrame && highSupportsCustomization
  }
}