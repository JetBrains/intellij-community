// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

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
import com.intellij.ui.mac.MacFullScreenControlsManager
import com.intellij.ui.mac.MacMainFrameDecorator
import com.intellij.util.childScope
import com.intellij.util.ui.JBUI
import com.jetbrains.JBR
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JFrame

private const val GAP_FOR_BUTTONS = 80
private const val DEFAULT_HEADER_HEIGHT = 40

internal class MacToolbarFrameHeader(private val coroutineScope: CoroutineScope, private val frame: JFrame, private val root: IdeRootPane)
  : CustomHeader(frame), MainFrameCustomHeader, ToolbarHolder, UISettingsListener {
  private var toolbar: MainToolbar

  private var currentComponent: JComponent

  private val updateRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private var lastHeight = DEFAULT_HEADER_HEIGHT

  init {
    layout = AdjustableSizeCardLayout()
    root.addPropertyChangeListener(MacMainFrameDecorator.FULL_SCREEN, PropertyChangeListener { updateBorders() })

    toolbar = createToolBar(coroutineScope.childScope())
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
      customTitleBar.height = DEFAULT_HEADER_HEIGHT.toFloat()
      JBR.getWindowDecorations()!!.setCustomTitleBar(frame, customTitleBar)
    }

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      initToolbar()

      updateRequests.collect {
        initToolbar()
        withContext(Dispatchers.EDT) {
          toolbar.updateBackground()
          updateCustomTitleBar()
          revalidate()
        }
      }
    }
  }

  private fun createToolBar(coroutineScope: CoroutineScope): MainToolbar {
    val toolbar = MainToolbar(coroutineScope, frame)
    toolbar.layoutCallBack = { updateCustomTitleBar() }
    toolbar.isOpaque = false
    toolbar.addComponentListener(object: ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateCustomTitleBar()
        super.componentResized(e)
      }
    })
    return toolbar
  }

  override fun paint(g: Graphics) {
    ProjectWindowCustomizerService.getInstance().paint(window = frame, parent = this, g = g)
    super.paint(g)
  }

  override fun updateUI() {
    super.updateUI()

    if (parent != null) {
      scheduleUpdateToolbar()
      updateBorders()
    }
  }

  private suspend fun initToolbar() {
    val toolbarActionGroups = computeMainActionGroups()
    val mainToolbarActionSupplier = { toolbarActionGroups }
    val isCompactHeader = root.isCompactHeader(mainToolbarActionSupplier)
    if (!isCompactHeader) {
      toolbar.init(toolbarActionGroups, customTitleBar)
    }

    withContext(Dispatchers.EDT) {
      updateVisibleCard(isCompactHeader)
      updateSize(mainToolbarActionSupplier)
    }
  }

  override fun scheduleUpdateToolbar() {
    updateRequests.tryEmit(Unit)
  }

  private fun updateVisibleCard(isCompactHeader: Boolean) {
    if (isCompactHeader) {
      remove(currentComponent)

      val headerTitle = SimpleCustomDecorationPath(frame)
      headerTitle.isOpaque = false
      add(headerTitle, BorderLayout.CENTER)
      currentComponent = headerTitle
      updateBorders()
      revalidate()
      repaint()
    }
    else if (componentCount != 0 && currentComponent != toolbar) {
      remove(currentComponent)
      val coroutineScope = coroutineScope.childScope()
      toolbar = createToolBar(coroutineScope)
      add(toolbar)
      currentComponent = toolbar

      updateRequests.tryEmit(Unit)
      revalidate()
      repaint()
    }
  }

  override fun windowStateChanged() {
    super.windowStateChanged()
    updateBorders()
  }

  override fun addNotify() {
    super.addNotify()
    updateBorders()
  }

  override fun getComponent(): JComponent = this

  override fun getHeaderBackground(active: Boolean): Color {
    return JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(active)
  }

  override fun updateCustomTitleBar() {
    val height = height
    if (height != 0 && lastHeight != height) {
      customTitleBar?.let {
        it.height = height.toFloat()
      }
    }
    updateBorders()
  }

  private fun updateBorders() {
    val isFullscreen = root.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) != null
    if (isFullscreen && !MacFullScreenControlsManager.enabled()) {
      border = JBUI.Borders.empty()
      ((if (componentCount == 0) null else getComponent(0)) as? SimpleCustomDecorationPath)?.updateBorders(0)
    }
    else {
      border = JBUI.Borders.emptyLeft(GAP_FOR_BUTTONS)
      ((if (componentCount == 0) null else getComponent(0)) as? SimpleCustomDecorationPath)?.updateBorders(GAP_FOR_BUTTONS)
    }
    toolbar.border = JBUI.Borders.empty()
  }

  override fun updateActive() {
    super.updateActive()
    toolbar.background = getHeaderBackground(isActive)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateRequests.tryEmit(Unit)
  }
}