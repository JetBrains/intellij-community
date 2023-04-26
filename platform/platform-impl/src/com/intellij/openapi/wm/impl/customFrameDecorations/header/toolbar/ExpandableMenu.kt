// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.impl.IdeMenuBar
import com.intellij.openapi.wm.impl.customFrameDecorations.header.FrameHeader
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.Alarm
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

private const val ALPHA = (255 * 0.8).toInt()
private const val MENU_HIDE_DELAY = 300

internal class ExpandableMenu(private val frameHeader: FrameHeader) {

  val ideMenu = IdeMenuBar.createMenuBar()
  lateinit var headerContent: JComponent
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

    frameHeader.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent?) {
        updateBounds()
      }
    })
  }

  fun isEnabled(): Boolean {
    return SystemInfoRt.isWindows && Registry.`is`("ide.windows.main.menu.expand.horizontal")
  }

  fun switchState(actionToShow: AnAction? = null) {
    if (expandedMenuBar != null && actionToShow == null) {
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

    var menu = ideMenu.getMenu(0)
    if (actionToShow != null) {
      for (i in 0..ideMenu.menuCount - 1) {
        val m = ideMenu.getMenu(i)
        if (m.mnemonic == actionToShow.templatePresentation.mnemonic) {
          menu = m
          break
        }
      }
    }
    val subElements = menu.popupMenu.subElements
    if (subElements.isNotEmpty()) {
      MenuSelectionManager.defaultManager().selectedPath = arrayOf(ideMenu, menu, menu.popupMenu, subElements[0])
    }
  }

  fun updateColor() {
    val color = frameHeader.background
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
    if (expandedMenuBar != null) {
      rootPane?.layeredPane?.remove(expandedMenuBar)
      expandedMenuBar = null

      rootPane?.repaint()
    }
  }

  private inner class CloseExpandedMenuAction : DumbAwareAction(
    IdeBundle.messagePointer("main.toolbar.expanded.menu.close"), ExpUiIcons.General.Close) {

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