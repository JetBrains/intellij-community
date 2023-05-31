// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceRangeToWithUntil")

package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.util.IJSwingUtilities
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.*
import javax.swing.*

private const val ALPHA = (255 * 0.6).toInt()

internal class ExpandableMenu(private val headerContent: JComponent) {

  val ideMenu: IdeMenuBar = IdeMenuBar.createMenuBar()
  private val ideMenuHelper = IdeMenuHelper(ideMenu, null)
  private var expandedMenuBar: JPanel? = null
  private var headerColorfulPanel: HeaderColorfulPanel? = null
  private val shadowComponent = ShadowComponent()
  private val rootPane: JRootPane?
    get() = SwingUtilities.getRootPane(headerContent)
  private var hideMenu = false

  init {
    MenuSelectionManager.defaultManager().addChangeListener {
      if (MenuSelectionManager.defaultManager().selectedPath.isNullOrEmpty()) {
        // After resetting selectedPath another menu can be shown right after that, so don't hide main menu immediately
        hideMenu = true
        ApplicationManager.getApplication().invokeLater {
          if (hideMenu) {
            hideMenu = false
            hideExpandedMenuBar()
          }
        }
      } else {
        hideMenu = false
      }
    }

    ideMenuHelper.installListeners()

    headerContent.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateBounds()
      }
    })
  }

  fun isEnabled(): Boolean {
    return !SystemInfoRt.isMac && Registry.`is`("ide.main.menu.expand.horizontal")
  }

  private fun isShowing(): Boolean {
    return expandedMenuBar != null
  }

  private fun updateUI() {
    IJSwingUtilities.updateComponentTreeUI(ideMenu)
    ideMenu.border = null
    ideMenuHelper.updateUI()
  }

  fun switchState(buttonSize: Dimension, actionToShow: AnAction? = null) {
    if (isShowing() && actionToShow == null) {
      hideExpandedMenuBar()
      return
    }

    hideExpandedMenuBar()
    val layeredPane = rootPane?.layeredPane ?: return

    expandedMenuBar = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          val closeButton = createMenuButton(CloseExpandedMenuAction()).apply {
            setMinimumButtonSize(JBDimension(JBUI.unscale(buttonSize.width), JBUI.unscale(buttonSize.height)))
          }
          headerColorfulPanel = cell(HeaderColorfulPanel(listOf(closeButton, ideMenu)))
            .align(AlignY.FILL)
            .component
          cell(shadowComponent).align(Align.FILL)
        }
      }
    }.apply { isOpaque = false }

    ideMenu.updateMenuActions(true)

    // menu wasn't a part of component's tree, updateUI is needed
    updateUI()
    updateBounds()
    updateColor()
    layeredPane.add(expandedMenuBar!!, (JLayeredPane.DEFAULT_LAYER - 2) as Any)

    // First menu usage has no selection in menu. Fix it by invokeLater
    ApplicationManager.getApplication().invokeLater {
      selectMenu(actionToShow)
    }
  }

  private fun selectMenu(action: AnAction? = null) {
    var menu = ideMenu.getMenu(0)
    if (action != null) {
      for (i in 0..ideMenu.menuCount - 1) {
        val m = ideMenu.getMenu(i)
        if (m.mnemonic == action.templatePresentation.mnemonic) {
          menu = m
          break
        }
      }
    }

    val subElements = menu.popupMenu.subElements
    if (subElements.isEmpty()) {
      MenuSelectionManager.defaultManager().selectedPath = arrayOf(ideMenu, menu)
    }
    else {
      MenuSelectionManager.defaultManager().selectedPath = arrayOf(ideMenu, menu, menu.popupMenu, subElements[0])
    }
  }

  fun updateColor() {
    val color = headerContent.background
    headerColorfulPanel?.background = color
    @Suppress("UseJBColor")
    shadowComponent.background = Color(color.red, color.green, color.blue, ALPHA)
  }

  private fun updateBounds() {
    val location = SwingUtilities.convertPoint(headerContent, 0, 0, rootPane ?: return)
    if (location == null) {
      headerColorfulPanel?.horizontalOffset = 0
    } else {
      val insets = headerContent.insets
      headerColorfulPanel?.horizontalOffset = location.x + insets.left
      expandedMenuBar?.let {
        it.bounds = Rectangle(location.x + insets.left, location.y + insets.top,
                              headerContent.width - insets.left - insets.right,
                              headerContent.height - insets.top - insets.bottom)
      }
    }
  }

  private fun hideExpandedMenuBar() {
    if (isShowing()) {
      rootPane?.layeredPane?.remove(expandedMenuBar)
      expandedMenuBar = null
      headerColorfulPanel = null

      rootPane?.repaint()
    }
  }

  private class HeaderColorfulPanel(components: List<JComponent>): JPanel() {

    var horizontalOffset = 0

    init {
      // Deny background painting by super.paint()
      isOpaque = false
      layout = GridLayout()
      val builder = RowsGridBuilder(this)
      for (component in components) {
        builder.cell(component, verticalAlign = VerticalAlign.FILL)
      }
    }

    override fun paint(g: Graphics?) {
      g as Graphics2D
      g.color = background
      g.fillRect(0, 0, width, height)
      g.translate(-horizontalOffset, 0)
      val root = SwingUtilities.getRoot(this) as? Window
      if (root == null) false else ProjectWindowCustomizerService.getInstance().paint(root, this, g)
      g.translate(horizontalOffset, 0)
      super.paint(g)
    }
  }

  private inner class CloseExpandedMenuAction : DumbAwareAction(ExpUiIcons.General.Close) {

    override fun actionPerformed(e: AnActionEvent) {
      hideExpandedMenuBar()
    }
  }

  private inner class ShadowComponent : JComponent() {

    init {
      isOpaque = false

      addMouseListener(object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent?) {
          hideExpandedMenuBar()
        }
      })
    }

    override fun paint(g: Graphics?) {
      g ?: return
      g.color = background
      g.fillRect(0, 0, width, height)
    }
  }
}