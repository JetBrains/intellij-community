// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.ToolbarHolder
import com.intellij.openapi.wm.impl.customFrameDecorations.header.AbstractMenuFrameHeader
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.headertoolbar.isToolbarInHeader
import com.intellij.ui.IconManager
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.hover.addHoverAndPressStateListener
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.CurrentTheme.CustomFrameDecorations
import java.awt.*
import java.awt.GridBagConstraints.*
import javax.swing.*
import kotlin.math.roundToInt

private enum class ShowMode {
  MENU, TOOLBAR
}

internal class ToolbarFrameHeader(frame: JFrame, ideMenu: IdeMenuBar) : AbstractMenuFrameHeader(frame), UISettingsListener, ToolbarHolder {

  private val myMenuBar = ideMenu
  private val myMenuButton = createMenuButton()
  private var myToolbar : MainToolbar? = null
  private val myToolbarPlaceholder = NonOpaquePanel()
  private val myHeaderContent = createHeaderContent()

  private var myMode = ShowMode.MENU

  init {
    layout = GridBagLayout()
    val gb = GridBag().anchor(WEST)

    updateLayout(UISettings.instance)

    productIcon.border = JBUI.Borders.empty(V, H)
    add(productIcon, gb.nextLine().next().anchor(WEST))
    add(myHeaderContent, gb.next().fillCell().anchor(CENTER).weightx(1.0).weighty(1.0))
    add(buttonPanes.getView(), gb.next().anchor(EAST))

    setCustomFrameTopBorder({ false }, {true})
  }

  override fun updateToolbar() {
    removeToolbar()
    myToolbar = createToolbar()
    myToolbarPlaceholder.add(myToolbar)
    myToolbarPlaceholder.revalidate()
  }

  override fun removeToolbar() {
    myToolbarPlaceholder.removeAll()
    myToolbarPlaceholder.revalidate()
  }

  override fun updateMenuActions(forceRebuild: Boolean) {} //todo remove

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateLayout(uiSettings)
    when (myMode) {
      ShowMode.TOOLBAR -> updateToolbar()
      ShowMode.MENU -> removeToolbar()
    }
  }

  override fun getHitTestSpots(): List<RelativeRectangle> {
    val res = super.getHitTestSpots().toMutableList()

    when (myMode) {
      ShowMode.MENU -> {
        res.add(getElementRect(myMenuBar) { rect ->
          val state = frame.extendedState
          if (state != Frame.MAXIMIZED_VERT && state != Frame.MAXIMIZED_BOTH) {
            val topGap = (rect.height / 3).toFloat().roundToInt()
            rect.y += topGap
            rect.height -= topGap
          }
        })
      }
      ShowMode.TOOLBAR -> {
        res.add(getElementRect(myMenuButton))
        myToolbar?.components?.filter { it.isVisible }?.forEach { res.add(getElementRect(it)) }
      }
    }

    return res
  }

  override fun getHeaderBackground(active: Boolean) = CustomFrameDecorations.mainToolbarBackground(active)

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
      add(myToolbarPlaceholder, gb.next().weightx(1.0).fillCellHorizontally())
    }

    res.add(ShowMode.MENU.name, menuPnl)
    res.add(ShowMode.TOOLBAR.name, toolbarPnl)

    return res
  }

  private fun updateLayout(settings: UISettings) {
    myMode = if (isToolbarInHeader(settings)) ShowMode.TOOLBAR else ShowMode.MENU
    val layout = myHeaderContent.layout as CardLayout
    layout.show(myHeaderContent, myMode.name)
  }

  private fun createToolbar(): MainToolbar {
    val project = (frame as? IdeFrame)?.project
    val toolbar = MainToolbar(project)
    toolbar.isOpaque = false
    return toolbar
  }

  private fun createMenuButton(): AbstractButton {
    val button = FixedSizeButton(36)
    val icon = IconManager.getInstance().getIcon("expui/general/windowsMenu.svg", AllIcons::class.java)
    if (icon is ScalableIcon) {
      button.icon = IconLoader.loadCustomVersionOrScale(icon, 20f) //todo change to precompiled icon later
    }

    button.isContentAreaFilled = false
    addHoverAndPressStateListener(button,
                                  hoveredStateCallback = { cmp, hovered ->
                                    if (cmp !is AbstractButton) return@addHoverAndPressStateListener
                                    if (hovered) {
                                      cmp.putClientProperty("JButton.backgroundColor", UIManager.getColor("MainToolbar.Icon.hoverBackground"))
                                      cmp.isContentAreaFilled = true
                                    }
                                    else {
                                      cmp.putClientProperty("JButton.backgroundColor", CustomFrameDecorations.mainToolbarBackground(true))
                                      cmp.isContentAreaFilled = false
                                    }
                                  },
                                  pressedStateCallback = { cmp, pressed ->
                                    if (cmp !is JComponent) return@addHoverAndPressStateListener
                                    if (pressed) {
                                      cmp.putClientProperty("JButton.backgroundColor", UIManager.getColor("MainToolbar.Icon.pressedBackground"))
                                    }
                                    else {
                                      cmp.putClientProperty("JButton.backgroundColor", UIManager.getColor("MainToolbar.Icon.hoverBackground"))
                                    }
                                  })

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
    button.putClientProperty("JButton.backgroundColor", getHeaderBackground())
    button.putClientProperty("ActionToolbar.smallVariant", true)

    button.toolTipText = IdeBundle.message("main.toolbar.menu.button")

    return button
  }
}
