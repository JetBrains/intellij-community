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
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.openapi.wm.impl.ToolbarHolder
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SimpleCustomDecorationPath
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.headertoolbar.computeMainActionGroups
import com.intellij.ui.UIBundle
import com.intellij.ui.mac.MacFullScreenControlsManager
import com.intellij.ui.mac.MacMainFrameDecorator
import com.intellij.util.childScope
import com.intellij.util.ui.JBUI
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.accessibility.AccessibleContext
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

private const val GAP_FOR_BUTTONS = 80

private sealed interface HeaderView {
  fun createComponent(parentCoroutineScope: CoroutineScope, frame: JFrame): JComponent
}

private object ToolbarHeaderView : HeaderView {
  override fun createComponent(parentCoroutineScope: CoroutineScope, frame: JFrame): JComponent {
    val component = MainToolbar(coroutineScope = parentCoroutineScope.childScope(), frame = frame)
    component.border = JBUI.Borders.empty()
    return component
  }
}

private object CompactHeaderView : HeaderView {
  override fun createComponent(parentCoroutineScope: CoroutineScope, frame: JFrame): JComponent {
    val headerTitle = SimpleCustomDecorationPath(frame)
    headerTitle.isOpaque = false
    headerTitle.background = null
    return headerTitle
  }
}

internal class MacToolbarFrameHeader(private val coroutineScope: CoroutineScope, private val frame: JFrame, private val root: IdeRootPane)
  : JPanel(), MainFrameCustomHeader, ToolbarHolder, UISettingsListener {
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

  private val customTitleBar: WindowDecorations.CustomTitleBar?

  init {
    // color full toolbar
    isOpaque = false
    background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true)

    val windowDecorations = JBR.getWindowDecorations()
    customTitleBar = windowDecorations?.createCustomTitleBar()

    root.addPropertyChangeListener(MacMainFrameDecorator.FULL_SCREEN, PropertyChangeListener { updateBorders() })

    view = ToolbarHeaderView

    layout = AdjustableSizeCardLayout(heightProvider = ::getPreferredHeight)

    add(view.createComponent(coroutineScope, frame), BorderLayout.CENTER)
    updateBorders()

    if (customTitleBar != null) {
      customTitleBar.height = HEADER_HEIGHT_NORMAL.toFloat()
      windowDecorations.setCustomTitleBar(frame, customTitleBar)
    }

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      val isCompactHeader = root.isCompactHeader(mainToolbarActionSupplier = { computeMainActionGroups() })

      val newView = if (isCompactHeader) CompactHeaderView else ToolbarHeaderView
      if (isCompactHeader) {
        updateView(newView)
      }
      else {
        (getComponent(0) as MainToolbar).init(customTitleBar)
      }

      updateRequests.collect {
        updateState()
      }
    }

    MacFullScreenControlsManager.configureEnable(coroutineScope) {
      updateBorders()
    }

    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      if (root.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) != null) {
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

  private fun getPreferredHeight(): Int {
    return JBUI.scale(
      when {
        view == CompactHeaderView -> HEADER_HEIGHT_DFM
        UISettings.getInstance().compactMode -> HEADER_HEIGHT_COMPACT
        else -> HEADER_HEIGHT_NORMAL
      }
    )
  }

  private suspend fun updateState() {
    val isCompactHeader = root.isCompactHeader(mainToolbarActionSupplier = { computeMainActionGroups() })
    updateView(if (isCompactHeader) CompactHeaderView else ToolbarHeaderView)
  }

  override fun paintComponent(g: Graphics) {
    if (view == ToolbarHeaderView && ProjectWindowCustomizerService.getInstance().paint(window = frame, parent = this, g = g)) {
      return
    }

    // isOpaque is false to paint colorful toolbar gradient, so, we have to draw background on our own
    g.color = background
    g.fillRect(0, 0, parent.width, parent.height)
  }

  override fun updateUI() {
    super.updateUI()

    customTitleBar?.let {
      updateWinControlsTheme(background = background, customTitleBar = it)
    }

    if (parent != null) {
      scheduleUpdateToolbar()
      updateBorders()
    }
  }

  override fun scheduleUpdateToolbar() {
    updateRequests.tryEmit(Unit)
  }

  private suspend fun updateView(newView: HeaderView) {
    val component = withContext(Dispatchers.EDT) {
      if (view == newView) {
        return@withContext null
      }

      view = newView

      removeAll()

      val component = view.createComponent(coroutineScope, frame)
      add(component, BorderLayout.CENTER)

      if (customTitleBar != null) {
        customTitleBar.height = getPreferredHeight().toFloat()
      }

      updateBorders()
      revalidate()
      repaint()
      component
    } ?: return

    if (newView == ToolbarHeaderView) {
      (component as MainToolbar).init(customTitleBar)
    }
  }

  override fun getComponent(): JComponent = this

  private fun updateBorders() {
    val isFullscreen = root.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) != null
    val rightGap: Int
    if (isFullscreen && !MacFullScreenControlsManager.enabled()) {
      border = JBUI.Borders.empty()
      rightGap = 0
    }
    else {
      border = JBUI.Borders.emptyLeft(GAP_FOR_BUTTONS)
      rightGap = GAP_FOR_BUTTONS
    }

    val component = getComponent(0)
    if (component !is MainToolbar) {
      (component as SimpleCustomDecorationPath).updateBorders(rightGap)
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