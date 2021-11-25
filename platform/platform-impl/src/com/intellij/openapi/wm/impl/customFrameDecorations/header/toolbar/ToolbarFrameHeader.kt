// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.customFrameDecorations.header.AbstractMenuFrameHeader
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.ui.IconManager
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import java.awt.*
import java.awt.GridBagConstraints.*
import javax.swing.AbstractButton
import javax.swing.Box
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.math.roundToInt

private const val MENU_PANEL = "menu"
private const val TOOLBAR_PANEL = "toolbar"

internal class ToolbarFrameHeader(frame: JFrame, ideMenu: IdeMenuBar) : AbstractMenuFrameHeader(frame), UISettingsListener {

  private val myMenuBar = ideMenu
  private val myMenuButton = createMenuButton()
  private val myToolbar = createToolbar()
  private val myHeaderContent = createHeaderContent()

  init {
    layout = GridBagLayout()
    val gb = GridBag().anchor(WEST)

    updateHeaderContent(UISettings.instance)

    productIcon.border = JBUI.Borders.empty(V, H)
    add(productIcon, gb.nextLine().next().anchor(WEST))
    add(myHeaderContent, gb.next().fillCell().anchor(CENTER).weightx(1.0).weighty(1.0))
    add(buttonPanes.getView(), gb.next().anchor(EAST))

    setCustomFrameTopBorder({ false }, {true})
  }

  override fun updateMenuActions(forceRebuild: Boolean) {} //todo remove

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateHeaderContent(uiSettings)
  }

  override fun getHitTestSpots(): List<RelativeRectangle> {
    val res = super.getHitTestSpots().toMutableList()

    if (myMenuBar.isVisible) {
      res.add(getElementRect(myMenuBar) { rect ->
        val state = frame.extendedState
        if (state != Frame.MAXIMIZED_VERT && state != Frame.MAXIMIZED_BOTH) {
          val topGap = (rect.height / 3).toFloat().roundToInt()
          rect.y += topGap
          rect.height -= topGap
        }
      })
    }

    if (myMenuButton.isVisible) res.add(getElementRect(myMenuButton))
    if (myToolbar.isVisible) {
      for (cmp in myToolbar.components) res.add(getElementRect(cmp))
    }

    return res
  }

  private fun getElementRect(comp: Component, rectProcessor: (Rectangle) -> Unit = {}): RelativeRectangle {
    val rect = Rectangle(comp.size)
    rectProcessor(rect)
    return RelativeRectangle(comp, rect)
  }

  fun createHeaderContent(): JPanel {
    val res = NonOpaquePanel(CardLayout())
    res.border = JBUI.Borders.emptyLeft(15)

    val menuPnl = NonOpaquePanel(GridBagLayout()).apply {
      val gb = GridBag().anchor(WEST).nextLine()
      add(myMenuBar, gb.next())
      add(Box.createHorizontalGlue(), gb.next().weightx(1.0).fillCell())
    }
    val toolbarPnl = NonOpaquePanel(GridBagLayout()).apply {
      val gb = GridBag().anchor(WEST).nextLine()
      add(myMenuButton, gb.next().insetRight(25))
      add(myToolbar, gb.next().weightx(1.0).fillCellHorizontally())
    }

    res.add(MENU_PANEL, menuPnl)
    res.add(TOOLBAR_PANEL, toolbarPnl)

    return res
  }

  private fun updateHeaderContent(settings: UISettings) {
    val layout = myHeaderContent.layout as CardLayout
    layout.show(myHeaderContent, if (settings.showMainToolbar) MENU_PANEL else TOOLBAR_PANEL)
  }

  private fun createToolbar(): JPanel = MainToolbar().apply { isOpaque = false }

  private fun createMenuButton(): AbstractButton {
    val button = FixedSizeButton(20)
    button.icon = IconManager.getInstance().getIcon("expui/20x20/general/windowsMenu.svg", AllIcons::class.java) //todo change to precompiled icon later
    button.isOpaque = false
    button.addActionListener {
      DataManager.getInstance().dataContextFromFocusAsync.blockingGet(200)?.let { context ->
        val mainMenu = ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup
        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, mainMenu, context, false, true,
            false, null, 30, null)
          .showUnderneathOf(button)
      }
    }

    button.border = JBUI.Borders.empty()
    button.putClientProperty("JButton.backgroundColor", CustomFrameDecorations.titlePaneBackground())
    button.putClientProperty("ActionToolbar.smallVariant", true)

    return button
  }
}
