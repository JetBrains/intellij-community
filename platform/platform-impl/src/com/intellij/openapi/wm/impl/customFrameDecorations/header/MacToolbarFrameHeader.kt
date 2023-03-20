// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ide.actions.ToggleDistractionFreeModeAction
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.ToolbarHolder
import com.intellij.openapi.wm.impl.customFrameDecorations.CustomFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SimpleCustomDecorationPath
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.mac.MacMainFrameDecorator
import com.intellij.util.ui.JBUI
import com.jetbrains.CustomWindowDecoration
import com.jetbrains.JBR
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane


private const val GAP_FOR_BUTTONS = 80
private const val DEFAULT_HEADER_HEIGHT = 40

internal class MacToolbarFrameHeader(private val frame: JFrame,
                                     private val root: JRootPane) : CustomHeader(frame), MainFrameCustomHeader, ToolbarHolder, UISettingsListener {
  private val ideMenu: IdeMenuBar = IdeMenuBar()
  private var toolbar: MainToolbar? = null
  private val headerTitle = SimpleCustomDecorationPath(frame)

  private val TOOLBAR_CARD = "TOOLBAR_CARD"
  private val PATH_CARD = "PATH_CARD"

  init {
    layout = AdjustableSizeCardLayout()
    root.addPropertyChangeListener(MacMainFrameDecorator.FULL_SCREEN, PropertyChangeListener { updateBorders() })
    add(ideMenu)

    addHeaderTitle()
    toolbar = createToolBar()
    updateVisibleCard()

    val rKey = Registry.get("apple.awt.newFullScreeControls")
    System.setProperty(rKey.key, java.lang.Boolean.toString(rKey.asBoolean()))
    rKey.addListener(
      object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          System.setProperty(rKey.key, java.lang.Boolean.toString(rKey.asBoolean()))
          updateBorders()
        }
      }, this)
  }

  private fun createToolBar(): MainToolbar {
    val toolbar = MainToolbar()
    toolbar.layoutCallBack = { updateCustomDecorationHitTestSpots() }
    toolbar.isOpaque = false
    toolbar.addComponentListener(object: ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateCustomDecorationHitTestSpots()
        super.componentResized(e)
      }
    })
    add(toolbar, TOOLBAR_CARD)
    return toolbar
  }

  private fun addHeaderTitle() {
    headerTitle.isOpaque = false
    add(headerTitle, PATH_CARD)
    updateBorders()
  }

  override fun updateUI() {
    super.updateUI()

    if (parent != null) {
      updateToolbar()
      updateBorders()
    }
  }

  override fun initToolbar(toolbarActionGroups: List<Pair<ActionGroup, String>>) {
    toolbar?.init(toolbarActionGroups)
  }

  override fun updateToolbar() {
    var toolbar = toolbar ?: return
    remove(toolbar)
    toolbar = createToolBar()
    this.toolbar = toolbar
    toolbar.init(MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()))

    revalidate()
    updateCustomDecorationHitTestSpots()

    updateVisibleCard()
  }

  private fun updateVisibleCard() {
    val cardToShow = if (ToggleDistractionFreeModeAction.shouldMinimizeCustomHeader()) PATH_CARD else TOOLBAR_CARD
    (getLayout() as? CardLayout)?.show(this, cardToShow)

    revalidate()
    repaint()
  }

  override fun windowStateChanged() {
    super.windowStateChanged()
    updateBorders()
  }

  override fun addNotify() {
    super.addNotify()
    updateBorders()

    val decor = JBR.getCustomWindowDecoration()
    decor.setCustomDecorationTitleBarHeight(frame, DEFAULT_HEADER_HEIGHT)
  }

  override fun createButtonsPane(): CustomFrameTitleButtons = CustomFrameTitleButtons.create(myCloseAction)

  override fun getHitTestSpots(): Sequence<Pair<RelativeRectangle, Int>> {
    return (toolbar ?: return emptySequence())
      .components
      .asSequence()
      .filter { it.isVisible }
      .map { Pair(getElementRect(it), CustomWindowDecoration.MENU_BAR) }
  }

  override fun updateMenuActions(forceRebuild: Boolean) = ideMenu.updateMenuActions(forceRebuild)

  override fun getComponent(): JComponent = this

  override fun dispose() {}

  private fun getElementRect(comp: Component): RelativeRectangle {
    val rect = Rectangle(comp.size)
    return RelativeRectangle(comp, rect)
  }

  override fun getHeaderBackground(active: Boolean) = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(active)

  private fun updateBorders() {
    val isFullscreen = root.getClientProperty(MacMainFrameDecorator.FULL_SCREEN) != null
    if (isFullscreen && !Registry.`is`("apple.awt.newFullScreeControls")) {
      border = JBUI.Borders.empty()
      headerTitle.updateBorders(0)
    }
    else {
      border = JBUI.Borders.emptyLeft(GAP_FOR_BUTTONS)
      headerTitle.updateBorders(GAP_FOR_BUTTONS)
    }
    toolbar?.let { it.border = JBUI.Borders.empty() }
  }

  override fun updateActive() {
    super.updateActive()
    toolbar?.background = getHeaderBackground(myActive)
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateVisibleCard()
  }
}