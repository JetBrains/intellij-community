// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.ide.dnd.DnDNativeTarget
import com.intellij.openapi.application.EDT
import com.intellij.platform.debugger.impl.rpc.XMixedModeApi
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.impl.ui.SessionTabComponentProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds

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
@OptIn(FlowPreview::class)
@ApiStatus.Internal
class XSplitterWatchesViewImpl(
    sessionProxy: XDebugSessionProxy,
    watchesInVariables: Boolean,
    isVertical: Boolean,
    withToolbar: Boolean,
) : XWatchesViewImpl(sessionProxy, watchesInVariables, isVertical, withToolbar), DnDNativeTarget, XWatchesView {

  companion object {
    private const val proportionKey = "debugger.immediate.window.in.watches.proportion.key"
  }

  lateinit var splitter: OnePixelSplitter
    private set

  private var myPanel: BorderLayoutPanel? = null
  private var customized = true
  private var localsPanel: JComponent? = null
  private val updateRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var cachedIsMixedMode: Boolean? = null

  init {
    sessionProxy.coroutineScope.launch {
      updateRequests
        .debounce(100.milliseconds)
        .collectLatest {
          try {
            withContext(Dispatchers.EDT) { updateView() }
          }
          catch (_: Throwable) {
          }
        }
    }
  }


  override fun createMainPanel(localsPanelComponent: JComponent): JPanel {
    customized = true
    localsPanel = localsPanelComponent

    addMixedModeListenerIfNeeded()
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

    val evaluatorComponent = SessionTabComponentProvider.getInstance().createBottomLocalsComponent(sessionProxy!!)

    splitter = OnePixelSplitter(true, proportionKey, 0.01f, 0.99f)

    splitter.firstComponent = localsPanel
    splitter.secondComponent = evaluatorComponent

    myPanel.addToCenter(splitter)
  }

  private fun addMixedModeListenerIfNeeded() {
    sessionProxy!!.coroutineScope.launch(Dispatchers.EDT) {
      if (!isMixedModeSession())
        return@launch

      sessionProxy!!.addSessionListener(object : XDebugSessionListener {
        override fun stackFrameChanged() {
          updateViewIfNeeded()
        }

        override fun sessionPaused() {
          updateViewIfNeeded()
        }

        private fun updateViewIfNeeded() {
          sessionProxy!!.coroutineScope.launch(Dispatchers.EDT) {
            updateRequests.emit(Unit)
          }
        }
      }, this@XSplitterWatchesViewImpl)

      // Since we add the listener asynchronously, we may have missed an event,
      // forcing update to make sure we are synced
      updateRequests.emit(Unit)
    }
  }

  private suspend fun updateView() {
    val showCustomizedView = getShowCustomized()
    if (customized == showCustomizedView) return

    customized = showCustomizedView
    updateMainPanel()
  }

  private suspend fun getShowCustomized(): Boolean {
    if (!isMixedModeSession())
      return true

    val currentFrame = sessionProxy!!.getCurrentStackFrame() ?: return false
    return XDebugManagerProxy.getInstance()
      .withId(currentFrame, sessionProxy!!) { XMixedModeApi.getInstance().shouldShowCustomizedEvaluatorView(it) }
  }

  private suspend fun isMixedModeSession(): Boolean = cachedIsMixedMode ?: run {
    val value = XMixedModeApi.getInstance().isMixedModeSession(sessionProxy!!.id)
    cachedIsMixedMode = value
    value
  }
}