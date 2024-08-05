// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.wm.impl.ToolbarHolder
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.isCompactHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SimpleCustomDecorationPath
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.headertoolbar.computeMainActionGroups
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.UIBundle
import com.intellij.ui.mac.MacFullScreenControlsManager
import com.intellij.ui.mac.MacMainFrameDecorator
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.accessibility.AccessibleContext
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.JRootPane
import kotlin.math.roundToInt

// Fullscreen controls have fixed 52 points width, and scalable 13 points left and right gaps
private val GAP_FOR_BUTTONS: Int get() = 26 +  JBValue.Float(52f, true).unscaled.roundToInt()

internal class MacToolbarFrameHeader(
  private val coroutineScope: CoroutineScope,
  private val frame: JFrame,
  private val rootPane: JRootPane,
  private val isAlwaysCompact: Boolean = false,
) : JPanel(), MainFrameCustomHeader, ToolbarHolder, UISettingsListener {
  private var view: HeaderView

  private val updateRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val windowListener = object : WindowAdapter() {
    override fun windowActivated(ev: WindowEvent) {
      updateActive(isActive = true)
    }

    override fun windowDeactivated(ev: WindowEvent) {
      updateActive(isActive = false)
    }

    override fun windowStateChanged(e: WindowEvent) {
      updateBorders()
    }
  }

  val customTitleBar: WindowDecorations.CustomTitleBar?

  init {
    // color full toolbar
    isOpaque = false
    background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true)

    val windowDecorations = JBR.getWindowDecorations()
    customTitleBar = windowDecorations?.createCustomTitleBar()

    layout = object : GridBagLayout() {
      override fun preferredLayoutSize(parent: Container?): Dimension {
        val size = super.preferredLayoutSize(parent)
        size.height = getPreferredHeight()
        return size
      }
    }
    view = createView(isCompactHeaderFast())
    // view.init is called later in a separate coroutine - see below `coroutineScope.launch`

    updateBorders()

    if (customTitleBar != null) {
      CustomWindowHeaderUtil.configureCustomTitleBar(view is CompactHeaderView, customTitleBar, frame)
    }

    rootPane.addPropertyChangeListener(MacMainFrameDecorator.FULL_SCREEN, PropertyChangeListener { updateBorders() })

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      if (!updateView(isCompactHeader = isCompactHeader())) {
        // view is not updated - init the view that was created in our constructor
        view.init(customTitleBar)
      }

      updateRequests.collect {
        updateView(isCompactHeader = isCompactHeader())
      }
    }

    MacFullScreenControlsManager.configureEnable(coroutineScope) {
      updateBorders()
    }

    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      if (isFullScreen(rootPane)) {
        MacFullScreenControlsManager.updateColors(frame)
      }
    })

    frame.addWindowListener(windowListener)
    frame.addWindowStateListener(windowListener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      frame.removeWindowListener(windowListener)
      frame.removeWindowStateListener(windowListener)
    }
  }

  private fun isCompactHeaderFast(): Boolean {
    return isAlwaysCompact || isCompactHeader(UISettings.getInstance())
  }

  private suspend fun isCompactHeader(): Boolean {
    return isAlwaysCompact || isCompactHeader(UISettings.getInstance(), { computeMainActionGroups() })
  }

  private fun createView(isCompactHeader: Boolean): HeaderView {
    return if (isCompactHeader) {
      CompactHeaderView(this, frame, isFullScreen(rootPane))
    }
    else {
      ToolbarHeaderView(this, coroutineScope, frame, isFullScreen(rootPane))
    }
  }

  private fun getPreferredHeight(): Int {
    return CustomWindowHeaderUtil.getPreferredWindowHeaderHeight(view is CompactHeaderView)
  }

  override fun paintComponent(g: Graphics) {
    if (view is ToolbarHeaderView &&
        ProjectWindowCustomizerService.getInstance().paint(window = frame, parent = this, g = g as Graphics2D)) {
      return
    }

    // isOpaque is false to paint colorful toolbar gradient, so, we have to draw background on our own
    g.color = background
    g.fillRect(0, 0, width, height)
  }

  override fun updateUI() {
    super.updateUI()

    customTitleBar?.let {
      updateWinControlsTheme(background = background, customTitleBar = it)
    }

    if (parent != null) {
      updateBorders()
      view.onUpdateUi()
    }
  }

  override fun scheduleUpdateToolbar() {
    updateRequests.tryEmit(Unit)
  }

  private suspend fun updateView(isCompactHeader: Boolean): Boolean {
    val view = withContext(Dispatchers.EDT) {
      if (isCompactHeader == (view is CompactHeaderView)) {
        // IDEA-324521 Colored toolbar rendering is broken when enabling/disabling colored toolbar via main toolbar context menu
        repaint()
        return@withContext null
      }

      view.onRemove()
      removeAll()
      view = createView(isCompactHeader)

      revalidate()
      repaint()

      view
    } ?: return false

    view.init(customTitleBar)
    updateBorders()
    return true
  }

  override fun getComponent(): JComponent = this

  private fun updateBorders() {
    if (isFullScreen(rootPane) && !MacFullScreenControlsManager.enabled()) {
      view.updateBorders(5, 0)
    }
    else {
      view.updateBorders(GAP_FOR_BUTTONS, 0)
    }
  }

  override fun doLayout() {
    super.doLayout()

    // during opening project JBR loses some events and _deliverMoveResizeEvent is not happened
    // so we have swing frame with not empty bounds but with empty frame peer bounds and as result we have blank window
    // if native bounds is empty we push custom header height that leads to sets native bounds
    val height = height
    if (height != 0 && customTitleBar != null &&
        (Math.abs(customTitleBar.height - height) > 0.1 || MacUtil.isNativeBoundsEmpty(frame))) {
      customTitleBar.height = height.toFloat()
    }
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateRequests.tryEmit(Unit)
  }

  private fun updateActive(isActive: Boolean) {
    val headerBackground = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(isActive)
    background = headerBackground
    customTitleBar?.let {
      updateWinControlsTheme(background = headerBackground, customTitleBar = it)
    }
    revalidate()
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleCustomHeader()
      accessibleContext.accessibleName = UIBundle.message("frame.header.accessible.group.name")
    }
    return accessibleContext
  }

  private inner class AccessibleCustomHeader : AccessibleJPanel() {
    override fun getAccessibleRole() = AccessibilityUtils.GROUPED_ELEMENTS
  }
}

private fun isFullScreen(rootPane: JRootPane): Boolean = rootPane.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) != null

private sealed interface HeaderView {
  suspend fun init(customTitleBar: WindowDecorations.CustomTitleBar?) {
  }

  fun updateBorders(left: Int, right: Int) {
  }

  fun onUpdateUi() {
  }

  fun onRemove() {
  }
}

private class ToolbarHeaderView(private val container: JPanel, parentCoroutineScope: CoroutineScope, frame: JFrame, isFullScreen: Boolean) : HeaderView {
  private val toolbar: MainToolbar = MainToolbar(parentCoroutineScope.childScope(), frame) { isFullScreen }

  init {
    toolbar.border = JBUI.Borders.empty()
    container.add(toolbar, GridBagConstraints().also {
      it.gridx = 0
      it.gridy = 0
      it.weightx = 1.0
      it.weighty = 1.0
      it.fill = GridBagConstraints.BOTH
    })
  }

  override suspend fun init(customTitleBar: WindowDecorations.CustomTitleBar?) {
    toolbar.init(customTitleBar)
  }

  override fun updateBorders(left: Int, right: Int) {
    container.border = JBEmptyBorder(0, left, 0, right)
  }
}

private class CompactHeaderView(panel: JPanel, frame: JFrame, isFullScreen: Boolean) : HeaderView {
  private val headerTitle: SimpleCustomDecorationPath = SimpleCustomDecorationPath(frame)

  init {
    headerTitle.add(panel, if (isFullScreen && !MacFullScreenControlsManager.enabled()) 0 else GAP_FOR_BUTTONS)
  }

  override fun updateBorders(left: Int, right: Int) {
    headerTitle.updateBorders(left = left, right = right)
  }

  override fun onUpdateUi() {
    headerTitle.updateLabelForeground()
  }

  override fun onRemove() {
    headerTitle.onRemove()
  }
}