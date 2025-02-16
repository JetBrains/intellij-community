// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.application.EDT
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ExpandableMenu
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.MainMenuButton
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ShowMode
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ShowMode.Companion.isMergedMainMenu
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.messages.MessageBusConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentLinkedDeque
import javax.swing.Icon
import javax.swing.JFrame
import javax.swing.JMenu

class MainMenuWithButton(
  val coroutineScope: CoroutineScope, private val frame: JFrame,
) : NonOpaquePanel(GridLayout()) {
  val mainMenuButton: MainMenuButton = MainMenuButton(coroutineScope, getButtonIcon()) { if (ShowMode.isMergedMainMenu()) toolbarMainMenu.menuCount else 0 }
  val toolbarMainMenu: MergedMainMenu = MergedMainMenu(coroutineScope = coroutineScope, frame = frame).apply {
    addUpdateGlobalMenuRootsListener { components.forEach { (it as JMenu).isOpaque = false } }
    isOpaque = false
    isVisible = ShowMode.getCurrent() == ShowMode.TOOLBAR_WITH_MENU
    border = null
  }
  private val connection: MessageBusConnection = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus.connect()


  init {
    isOpaque = false
    RowsGridBuilder(this).defaultVerticalAlign(VerticalAlign.FILL)
      .row(resizable = true)
      .cell(component = toolbarMainMenu, resizableColumn = true)
      .cell(component = mainMenuButton.button)
    connection.subscribe(ToolbarCompressedNotifier.TOPIC, object : ToolbarCompressedListener {
      override fun onToolbarCompressed(event: ToolbarCompressedEvent) {
        recalculateWidth(event.toolbar)
      }
    })
  }

  private val toolbarInsetsConst = 20
  fun recalculateWidth(toolbar: MainToolbar?) {
    val isMergedMenu = isMergedMainMenu()
    toolbarMainMenu.isVisible = isMergedMenu
    mainMenuButton.button.presentation.icon = getButtonIcon()
    mainMenuButton.button.isVisible = !isMergedMenu || toolbarMainMenu.hasInvisibleItems()

    if (!isMergedMenu) return

    coroutineScope.launch(Dispatchers.EDT) {
      var wasChanged = false
      val toolbarPrefWidth = toolbar?.calculatePreferredWidth() ?: return@launch
      val menuButton = mainMenuButton.button
      val parentPanelWidth = menuButton.parent?.parent?.width ?: return@launch
      val menuWidth = toolbarMainMenu.components.sumOf { it.size.width }
      val menuButtonWidth = menuButton.preferredSize.width

      var availableWidth = parentPanelWidth - menuWidth - (menuButtonWidth.takeIf { menuButton.isVisible } ?: 0) - toolbarPrefWidth - JBUIScale.scale(toolbarInsetsConst)
      val rootMenuItems = toolbarMainMenu.rootMenuItems
      val widthLimit = if (menuButton.isVisible) 0 else menuButton.preferredSize.width

      if (availableWidth < 0) {
        var widthToFree = -(availableWidth - widthLimit)
        for (i in rootMenuItems.indices.reversed()) {
          if (toolbarMainMenu.rootMenuItems.size <= 1 || widthToFree <= 0) break

          val item = rootMenuItems[i]
          toolbarMainMenu.addInvisibleItem(item) // Add to removed items (LIFO behavior)
          toolbarMainMenu.remove(item)
          widthToFree -= item.size.width
          wasChanged = true
        }
      }

      else if (availableWidth > widthLimit) {
        while (availableWidth > widthLimit && toolbarMainMenu.hasInvisibleItems()) {
          val item = toolbarMainMenu.pollNextInvisibleItem(mainMenuButton.expandableMenu) ?: break // Remove the last item (LIFO order)
          val itemWidth = item.size.width
          if (availableWidth - itemWidth < widthLimit) {
            toolbarMainMenu.addInvisibleItem(item)
            break
          }

          toolbarMainMenu.add(item)
          availableWidth -= itemWidth
          wasChanged = true
        }
      }

      menuButton.isVisible = toolbarMainMenu.hasInvisibleItems()
      if (wasChanged) {
        if (toolbarMainMenu.rootMenuItems.isEmpty()) {
          val item = toolbarMainMenu.pollNextInvisibleItem(mainMenuButton.expandableMenu)
          toolbarMainMenu.add(item)
        }
        toolbarMainMenu.rootMenuItems.forEach { it.updateUI() }
        toolbar.revalidate()
        toolbar.repaint()
      }
    }
  }


  fun getButtonIcon(): Icon = if (isMergedMainMenu()) AllIcons.General.ChevronRight else AllIcons.General.WindowsMenu_20x20

  fun clearRemovedItems() {
    toolbarMainMenu.clearInvisibleItems()
  }
}

@ApiStatus.Internal
class MergedMainMenu(coroutineScope: CoroutineScope, frame: JFrame): IdeJMenuBar(coroutineScope = coroutineScope, frame = frame) {
  private val invisibleItems: ConcurrentLinkedDeque<ActionMenu> = ConcurrentLinkedDeque<ActionMenu>()

  fun clearInvisibleItems() {
    invisibleItems.clear()
  }

  fun addInvisibleItem(item: ActionMenu) {
    invisibleItems.offerLast(item) // Add to removed items (LIFO behavior)
  }

  internal fun pollNextInvisibleItem(expandableMenu: ExpandableMenu?): ActionMenu? {
    val expandableMenuNextItem = expandableMenu?.ideMenu?.rootMenuItems?.getOrNull(rootMenuItems.size)
    val lastItem = invisibleItems.last()
    val matchingItem = if (lastItem.text == expandableMenuNextItem?.text) lastItem else invisibleItems.find { it.text == expandableMenuNextItem?.text }
    matchingItem?.let {
      invisibleItems.remove(it)
    }
    return matchingItem

  }

  fun hasInvisibleItems(): Boolean = invisibleItems.isNotEmpty()

  fun getInvisibleItemsCount(): Int = invisibleItems.size
}