// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceRangeToWithUntil")

package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.ExpUiIcons
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
import com.intellij.util.Alarm
import com.intellij.util.IJSwingUtilities
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

private const val ALPHA = (255 * 0.6).toInt()
private const val MENU_HIDE_DELAY = 300

internal class ExpandableMenu(private val headerContent: JComponent) {

  val ideMenu = IdeMenuBar.createMenuBar()
  private var expandedMenuBar: JPanel? = null
  private val shadowComponent = ShadowComponent()
  private val alarm = Alarm()
  private val rootPane: JRootPane?
    get() = SwingUtilities.getRootPane(headerContent)

  init {
    MenuSelectionManager.defaultManager().addChangeListener {
      alarm.cancelAllRequests()
      if (MenuSelectionManager.defaultManager().selectedPath.isNullOrEmpty()) {
        // Right after resetting selectedPath another menu can be shown, so don't hide main menu immediately
        alarm.addRequest({ hideExpandedMenuBar() }, MENU_HIDE_DELAY)
      }
    }

    headerContent.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateBounds()
      }
    })
  }

  fun isEnabled(): Boolean {
    return !SystemInfoRt.isMac && Registry.`is`("ide.main.menu.expand.horizontal")
  }

  fun isShowing(): Boolean {
    return expandedMenuBar != null
  }

  fun updateUI() {
    IJSwingUtilities.updateComponentTreeUI(ideMenu)
  }

  fun switchState(actionToShow: AnAction? = null) {
    if (isShowing() && actionToShow == null) {
      hideExpandedMenuBar()
      return
    }

    hideExpandedMenuBar()
    val layeredPane = rootPane?.layeredPane ?: return

    expandedMenuBar = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          cell(wrapComponent(createMenuButton(CloseExpandedMenuAction())))
          cell(wrapComponent(ideMenu)).align(AlignY.FILL)
          cell(shadowComponent).align(Align.FILL)
        }
      }
    }.apply { isOpaque = false }

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
    if (action == null || subElements.isEmpty()) {
      MenuSelectionManager.defaultManager().selectedPath = arrayOf(ideMenu, menu)
    }
    else {
      MenuSelectionManager.defaultManager().selectedPath = arrayOf(ideMenu, menu, menu.popupMenu, subElements[0])
    }
  }

  fun updateColor() {
    val color = headerContent.background
    expandedMenuBar?.background = color
    @Suppress("UseJBColor")
    shadowComponent.background = Color(color.red, color.green, color.blue, ALPHA)
  }

  private fun wrapComponent(component: JComponent): JPanel {
    return JPanel(BorderLayout()).apply {
      add(component, BorderLayout.CENTER)
      background = null
    }
  }

  private fun updateBounds() {
    expandedMenuBar?.let {
      val insets = headerContent.insets
      val location = SwingUtilities.convertPoint(headerContent, Point(insets.left, insets.top), rootPane ?: return)
      it.bounds = Rectangle(location.x, location.y,
                            headerContent.width - insets.left - insets.right,
                            headerContent.height - insets.top - insets.bottom)
    }
  }

  private fun hideExpandedMenuBar() {
    if (isShowing()) {
      rootPane?.layeredPane?.remove(expandedMenuBar)
      expandedMenuBar = null

      rootPane?.repaint()
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