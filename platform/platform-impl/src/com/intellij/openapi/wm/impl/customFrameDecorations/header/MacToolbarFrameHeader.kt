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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.accessibility.AccessibleContext
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel

private const val GAP_FOR_BUTTONS = 80

internal class MacToolbarFrameHeader(private val coroutineScope: CoroutineScope, private val frame: JFrame, private val root: IdeRootPane)
  : JPanel(AdjustableSizeCardLayout()), MainFrameCustomHeader, ToolbarHolder, UISettingsListener {
  private var toolbar: MainToolbar?

  private var currentComponent: JComponent

  private val updateRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val windowListener = object : WindowAdapter() {
    override fun windowActivated(ev: WindowEvent) {
      updateActive()
    }

    override fun windowDeactivated(ev: WindowEvent) {
      updateActive()
    }

    override fun windowStateChanged(e: WindowEvent) {
      updateBorders()
    }
  }

  private val customTitleBar: WindowDecorations.CustomTitleBar?

  init {
    isOpaque = true
    background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true)

    val windowDecorations = JBR.getWindowDecorations()
    customTitleBar = windowDecorations?.createCustomTitleBar()

    root.addPropertyChangeListener(MacMainFrameDecorator.FULL_SCREEN, PropertyChangeListener { updateBorders() })

    val toolbar = createToolBar(coroutineScope.childScope())
    this.toolbar = toolbar
    add(toolbar, BorderLayout.CENTER)
    currentComponent = toolbar

    MacFullScreenControlsManager.configureEnable(coroutineScope) {
      updateBorders()
    }

    ApplicationManager.getApplication().messageBus.connect(coroutineScope).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      if (root.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) != null) {
        MacFullScreenControlsManager.updateColors(frame)
      }
    })

    ProjectWindowCustomizerService.getInstance().addListener(coroutineScope = coroutineScope, fireFirstTime = true) {
      isOpaque = !it
      revalidate()
    }

    if (customTitleBar != null) {
      customTitleBar.height = HEADER_HEIGHT_NORMAL.toFloat()
      windowDecorations.setCustomTitleBar(frame, customTitleBar)
    }

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      val toolbarActionGroups = computeMainActionGroups()
      val isCompactHeader = root.isCompactHeader(mainToolbarActionSupplier = { toolbarActionGroups })
      if (isCompactHeader) {
        withContext(Dispatchers.EDT) {
          updateVisibleComponent(isCompactHeader = true)
        }
      }
      else {
        toolbar.init(toolbarActionGroups, customTitleBar)
      }

      updateRequests.collect {
        updateState()
      }
    }
  }

  private suspend fun updateState() {
    val toolbarActionGroups = computeMainActionGroups()
    val mainToolbarActionSupplier = { toolbarActionGroups }
    val isCompactHeader = root.isCompactHeader(mainToolbarActionSupplier)

    withContext(Dispatchers.EDT) {
      updateVisibleComponent(isCompactHeader)
    }

    toolbar?.init(toolbarActionGroups, customTitleBar)
  }

  private fun createToolBar(coroutineScope: CoroutineScope) = MainToolbar(coroutineScope = coroutineScope, frame = frame)

  override fun paint(g: Graphics) {
    ProjectWindowCustomizerService.getInstance().paint(window = frame, parent = this, g = g)
    super.paint(g)
  }

  override fun updateUI() {
    super.updateUI()

    customTitleBar?.let {
      updateWinControlsTheme(panel = this, customTitleBar = it)
    }

    if (parent != null) {
      scheduleUpdateToolbar()
      updateBorders()
    }
  }

  override fun scheduleUpdateToolbar() {
    updateRequests.tryEmit(Unit)
  }

  private fun updateVisibleComponent(isCompactHeader: Boolean) {
    if (isCompactHeader) {
      if (toolbar == null) {
        return
      }

      remove(currentComponent)
      toolbar = null

      val headerTitle = SimpleCustomDecorationPath(frame)
      headerTitle.isOpaque = false
      add(headerTitle, BorderLayout.CENTER)
      currentComponent = headerTitle
      updateBorders()
    }
    else if (componentCount != 0 && currentComponent != toolbar) {
      remove(currentComponent)
      val coroutineScope = coroutineScope.childScope()
      val toolbar = createToolBar(coroutineScope)
      this.toolbar = toolbar
      add(toolbar)
      currentComponent = toolbar
    }
    else {
      return
    }

    val h = updatePreferredSize(isCompactHeader = { isCompactHeader }).height
    customTitleBar?.height = h.toFloat()

    revalidate()
    repaint()
  }

  override fun addNotify() {
    super.addNotify()
    updateActive()
    frame.addWindowListener(windowListener)
    frame.addWindowStateListener(windowListener)
    updateBorders()
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

    val toolbar = toolbar
    if (toolbar == null) {
      (currentComponent as? SimpleCustomDecorationPath)?.updateBorders(rightGap)
    }
    else {
      toolbar.border = JBUI.Borders.empty()
    }
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateRequests.tryEmit(Unit)
  }

  private fun updatePreferredSize(isCompactHeader: () -> Boolean): Dimension {
    val size = preferredSize
    size.height = JBUI.scale(
      when {
        isCompactHeader() -> HEADER_HEIGHT_DFM
        UISettings.getInstance().compactMode -> HEADER_HEIGHT_COMPACT
        else -> HEADER_HEIGHT_NORMAL
      }
    )
    preferredSize = size
    return size
  }

  override fun removeNotify() {
    super.removeNotify()
    frame.removeWindowListener(windowListener)
    frame.removeWindowStateListener(windowListener)
  }

  private fun updateActive() {
    background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(frame.isActive)
    customTitleBar?.let {
      updateWinControlsTheme(panel = this, customTitleBar = it)
    }
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