// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SimpleCustomDecorationPath
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.ui.mac.MacFullScreenControlsManager
import com.intellij.ui.mac.MacMainFrameDecorator
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.util.childScope
import com.intellij.util.ui.JBUI
import com.jetbrains.JBR
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

internal class MacToolbarFrameHeader(private val frame: JFrame, private val root: IdeRootPane)
  : CustomHeader(frame), MainFrameCustomHeader, ToolbarHolder, UISettingsListener {
  private val ideMenu: ActionAwareIdeMenuBar = if (FrameInfoHelper.isFloatingMenuBarSupported || !Menu.isJbScreenMenuEnabled()) {
    val menuBar = IdeMenuBar(root.coroutineScope.childScope(), frame)
    // if -DjbScreenMenuBar.enabled=false
    frame.jMenuBar = menuBar
    menuBar
  }
  else {
    MacMenuBar(coroutineScope = root.coroutineScope.childScope(), component = this, frame = frame)
  }

  private var toolbar: MainToolbar

  init {
    layout = AdjustableSizeCardLayout()
    root.addPropertyChangeListener(MacMainFrameDecorator.FULL_SCREEN, PropertyChangeListener { updateBorders() })

    toolbar = createToolBar()

    MacFullScreenControlsManager.configureEnable(this) {
      updateBorders()
    }

    ApplicationManager.getApplication().messageBus.connect(root.coroutineScope).subscribe(LafManagerListener.TOPIC, LafManagerListener {
      if (root.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) != null) {
        MacFullScreenControlsManager.updateColors(frame)
      }
    })

    ProjectWindowCustomizerService.getInstance().addListener(disposable = this, fireFirstTime = true) {
      isOpaque = !it
      revalidate()
    }

    JBR.getWindowDecorations()?.let { windowDecorations ->
      val bar = windowDecorations.createCustomTitleBar()
      bar.height = DEFAULT_HEADER_HEIGHT.toFloat()
      windowDecorations.setCustomTitleBar(frame, bar)
    }
  }

  private fun createToolBar(): MainToolbar {
    val toolbar = MainToolbar(root.coroutineScope.childScope(), frame)
    toolbar.layoutCallBack = { updateCustomTitleBar() }
    toolbar.isOpaque = false
    toolbar.addComponentListener(object: ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateCustomTitleBar()
        super.componentResized(e)
      }
    })
    add(toolbar, BorderLayout.CENTER)
    return toolbar
  }

  override fun paint(g: Graphics?) {
    ProjectWindowCustomizerService.getInstance().paint(frame, this, g)
    super.paint(g)
  }

  override fun updateUI() {
    super.updateUI()

    if (parent != null) {
      updateToolbar()
      updateBorders()
    }
  }

  override fun initToolbar(toolbarActionGroups: List<Pair<ActionGroup, String>>) {
    toolbar.init(toolbarActionGroups, customTitleBar)
    val mainToolbarActionSupplier = { toolbarActionGroups }
    updateVisibleCard(mainToolbarActionSupplier)
    updateSize(mainToolbarActionSupplier)
  }

  override fun updateToolbar() {
    var toolbar = toolbar
    remove(toolbar)
    toolbar = createToolBar()
    this.toolbar = toolbar
    val actionGroups = MainToolbar.computeActionGroups(CustomActionsSchema.getInstance())
    initToolbar(actionGroups)

    revalidate()
    updateCustomTitleBar()
  }

  private fun updateVisibleCard(mainToolbarActionSupplier: () -> List<Pair<ActionGroup, String>>) {
    if (root.isCompactHeader(mainToolbarActionSupplier)) {
      if (componentCount != 0) {
        remove(toolbar)
      }

      val headerTitle = SimpleCustomDecorationPath(frame)
      headerTitle.isOpaque = false
      add(headerTitle, BorderLayout.CENTER)
      updateBorders()
      revalidate()
      repaint()
    }
    else if (componentCount != 0 && getComponent(0) != toolbar) {
      remove(0)
      add(toolbar)
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

  override suspend fun updateMenuActions(forceRebuild: Boolean) {
    ideMenu.updateMenuActions(forceRebuild = forceRebuild)
  }

  override fun getComponent(): JComponent = this

  override fun dispose() {}

  override fun getHeaderBackground(active: Boolean): Color {
    return JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(active)
  }

  override fun updateCustomTitleBar() {
    super.updateCustomTitleBar()
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
    toolbar.background = getHeaderBackground(myActive)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateVisibleCard { MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()) }
  }
}