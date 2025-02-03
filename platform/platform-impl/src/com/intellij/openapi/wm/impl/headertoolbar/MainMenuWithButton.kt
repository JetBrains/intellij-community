// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.application.EDT
import com.intellij.openapi.wm.impl.RootPaneUtil
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.MainMenuButton
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ShowMode
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ShowMode.Companion.isMergedMainMenu
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.jetbrains.rd.util.collections.SynchronizedList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.Icon
import javax.swing.JFrame
import javax.swing.JMenu

class MainMenuWithButton(
  val coroutineScope: CoroutineScope, private val frame: JFrame,
) : NonOpaquePanel(GridLayout()) {
  val mainMenuButton: MainMenuButton = MainMenuButton(coroutineScope, getButtonIcon()) { if (ShowMode.isMergedMainMenu()) toolbarMainMenu.menuCount else 0 }
  val toolbarMainMenu: IdeJMenuBar = RootPaneUtil.createMenuBar(coroutineScope = coroutineScope, frame = frame, customMenuGroup = null).apply {
    addUpdateGlobalMenuRootsListener { components.forEach { (it as JMenu).isOpaque = false } }
    isOpaque = false
    isVisible = ShowMode.getCurrent() == ShowMode.TOOLBAR_WITH_MENU
    border = null
  }
  private val removedItems = SynchronizedList<ActionMenu>()

  init {
    isOpaque = false
    RowsGridBuilder(this).defaultVerticalAlign(VerticalAlign.FILL)
      .row(resizable = true)
      .cell(component = toolbarMainMenu, resizableColumn = true)
      .cell(component = mainMenuButton.button, gaps = UnscaledGaps(top = 5, bottom = 5))
  }

  fun recalculateWidth(toolbar: MainToolbar?) {
    val isMergedMenu = isMergedMainMenu()
    toolbarMainMenu.isVisible = isMergedMenu
    mainMenuButton.button.presentation.icon = getButtonIcon()
    if (isMergedMenu) {
      coroutineScope.launch(Dispatchers.EDT) {
        var wasChanged = false
        val toolbarPrefWidth = (toolbar?.calculatePreferredWidth() ?: return@launch)
        val menuButton = mainMenuButton.button
        val parentPanelWidth = menuButton.parent?.parent?.width ?: return@launch
        val menuWidth = toolbarMainMenu.components.sumOf { it.size.width }
        val menuButtonWidth = menuButton.preferredSize.width

        var availableWidth = parentPanelWidth - menuWidth - (menuButtonWidth.takeIf { menuButton.isVisible } ?: 0) - toolbarPrefWidth
        val rootMenuItems = toolbarMainMenu.rootMenuItems //when button is not visible we should keep in mind that it'll be visible on reduce, otherwise we already get it
        val widthLimit = if (menuButton.isVisible) 0 else menuButton.preferredSize.width
        if (availableWidth > widthLimit && removedItems.isNotEmpty()) {
          do {
            val item = removedItems.lastOrNull() ?: break
            val itemWidth = item.size.width
            if (availableWidth - itemWidth < widthLimit) break
            if (toolbarMainMenu.rootMenuItems.none { it.text == item.text }) toolbarMainMenu.add(item)
            removedItems.removeIf { it.text == item.text }
            availableWidth -= itemWidth
            wasChanged = true
          }
          while (availableWidth > widthLimit)
        }
        else if (availableWidth < 0 && rootMenuItems.count() > 1) {
          var widthToReduce = -(availableWidth - widthLimit)
          var ind = rootMenuItems.lastIndex
          do {
            val item = if (ind > 0) rootMenuItems[ind] else break
            if (removedItems.none { it.text == item.text }) removedItems.add(item)
            widthToReduce -= item.size.width
            ind--
            wasChanged = true
          }
          while (widthToReduce > 0)
          removedItems.forEach { removedItem ->
            toolbarMainMenu.rootMenuItems.find { it.text == removedItem.text }?.let { toolbarMainMenu.remove(it) }
          }
        }
        menuButton.isVisible = removedItems.isNotEmpty()
        if (wasChanged) {
          toolbarMainMenu.rootMenuItems.forEach { it.updateUI() }
          toolbar.revalidate()
          toolbar.repaint()
        }
      }
    }
  }

  fun getButtonIcon(): Icon = if (isMergedMainMenu()) AllIcons.General.ChevronRight else AllIcons.General.WindowsMenu_20x20

  fun clearRemovedItems() {
    removedItems.clear()
  }
}