// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.application
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.mixedmode.highLevelProcessOrThrow
import com.intellij.xdebugger.impl.mixedmode.lowLevelMixedModeExtensionOrThrow
import com.intellij.xdebugger.impl.mixedmode.lowLevelProcessOrThrow
import com.intellij.xdebugger.impl.ui.getBottomLocalsComponentProvider
import com.intellij.xdebugger.impl.ui.useSplitterView
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Allows customizing of variables view and splitting into 2 components.
 * Notice that you must provide the bottom component of the view by implementing XDebugSessionTabCustomizer in your XDebugProcess
 * @see com.intellij.xdebugger.impl.ui.XDebugSessionTabCustomizer.getBottomLocalsComponentProvider
 *
 * This component supports working in the mixed mode debugging,
 * If only one debug process provides a custom bottom component,
 * we will show the customized frame view only when a frame of this debug process is chosen.
 * When switching to a frame of a debug process that doesn't provide a custom bottom component, we will show a default frame view
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
      if (tryGetBottomComponentProvider(session, null) == null)
        error("XDebugProcess must provide a properly configured XDebugSessionTabCustomizer to use XSplitterWatchesViewImpl. Read JavaDoc for details")
    }

    private fun tryGetBottomComponentProvider(session: XDebugSessionImpl, useLowLevelDebugProcessPanel: Boolean?) =
      when (useLowLevelDebugProcessPanel) {
        null -> session.debugProcess
        true -> session.lowLevelProcessOrThrow
        false -> session.highLevelProcessOrThrow
      }.getBottomLocalsComponentProvider()

  }

  lateinit var splitter: OnePixelSplitter
    private set

  private var myPanel: BorderLayoutPanel? = null
  private var customized = true
  private var localsPanel : JComponent? = null

  override fun createMainPanel(localsPanelComponent: JComponent): JPanel {
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
    val bottomLocalsComponentProvider = tryGetBottomComponentProvider(session, useLowLevelDebugProcessPanel())
                                        ?: error("BottomLocalsComponentProvider is not implemented to use SplitterWatchesVariablesView")

    val evaluatorComponent = bottomLocalsComponentProvider.createBottomLocalsComponent()
    splitter = OnePixelSplitter(true, proportionKey, 0.01f, 0.99f)

    splitter.firstComponent = localsPanel
    splitter.secondComponent = evaluatorComponent

    myPanel.addToCenter(splitter)
  }

  private fun addMixedModeListenerIfNeeded(session: XDebugSessionImpl) {
    if (!session.isMixedMode) return

    val lowSupportsCustomization = session.lowLevelProcessOrThrow.useSplitterView()
    val highSupportsCustomization = session.highLevelProcessOrThrow.useSplitterView()
    if (lowSupportsCustomization == highSupportsCustomization) return

    session.addSessionListener(object : XDebugSessionListener {
      override fun stackFrameChanged() {
        updateView()
      }

      override fun sessionPaused() {
        updateView()
      }
    })
  }

  private fun updateView() {
    application.invokeLater {
      val showCustomizedView = getShowCustomized()
      if (customized == showCustomizedView) return@invokeLater

      customized = showCustomizedView
      updateMainPanel()
    }
  }

  private fun getShowCustomized(): Boolean {
    val session = session ?: return false
    if (!session.isMixedMode) return true

    val lowSupportsCustomization = session.lowLevelProcessOrThrow.useSplitterView()
    val highSupportsCustomization = session.highLevelProcessOrThrow.useSplitterView()

    val useLowLevelPanel = useLowLevelDebugProcessPanel() == true
    val useHighLevelPanel = !useLowLevelPanel
    return useLowLevelPanel && lowSupportsCustomization || useHighLevelPanel && highSupportsCustomization
  }

  private fun useLowLevelDebugProcessPanel(): Boolean? {
    val session = session ?: return null
    if (!session.isMixedMode) return null
    val frame = session.currentStackFrame ?: return false
    return session.lowLevelMixedModeExtensionOrThrow.belongsToMe(frame)
  }
}