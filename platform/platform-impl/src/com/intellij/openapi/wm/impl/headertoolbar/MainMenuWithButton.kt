// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.NlsSafe
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.util.concurrent.ConcurrentLinkedDeque
import javax.swing.*
import javax.swing.event.ChangeEvent

class MainMenuWithButton(
  val coroutineScope: CoroutineScope, private val frame: JFrame,
) : NonOpaquePanel(GridLayout()) {
  val mainMenuButton: MainMenuButton = MainMenuButton(coroutineScope, getButtonIcon()) { if (ShowMode.isMergedMainMenu()) toolbarMainMenu.menuCount else 0 }
  val toolbarMainMenu: MergedMainMenu = MergedMainMenu(coroutineScope = coroutineScope, frame = frame)

  init {
    isOpaque = false
    RowsGridBuilder(this).defaultVerticalAlign(VerticalAlign.FILL)
      .row(resizable = true)
      .cell(component = toolbarMainMenu, resizableColumn = true)
      .cell(component = mainMenuButton.button)
    supportKeyNavigationToFullMenu()
  }

  private val toolbarInsetsConst = 20
  fun recalculateWidth(toolbar: MainToolbar?) {
    val isMergedMenu = isMergedMainMenu()
    toolbarMainMenu.isVisible = isMergedMenu
    mainMenuButton.button.presentation.icon = getButtonIcon()
    val expandableMenu = mainMenuButton.expandableMenu
    mainMenuButton.button.isVisible = !isMergedMenu || toolbarMainMenu.hasInvisibleItems(expandableMenu)

    if (!isMergedMenu) return

    coroutineScope.launch(Dispatchers.EDT) {
      var wasChanged = false
      if (toolbarMainMenu.rootMenuItems.isEmpty() && toolbarMainMenu.hasInvisibleItems(expandableMenu)) {
        toolbarMainMenu.pollNextInvisibleItem(expandableMenu)?.let {
          toolbarMainMenu.add(it)
        }
        wasChanged = true
      }

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
        while (availableWidth > widthLimit && toolbarMainMenu.hasInvisibleItems(expandableMenu)) {
          val item = toolbarMainMenu.pollNextInvisibleItem(expandableMenu) ?: break // Remove the last item (LIFO order)
          val itemWidth = if(item.size.width > 0) item.size.width else item.preferredSize.width
          if (availableWidth - itemWidth < widthLimit) {
            toolbarMainMenu.addInvisibleItem(item)
            break
          }

          toolbarMainMenu.add(item)
          availableWidth -= itemWidth
          wasChanged = true
        }
      }

      menuButton.isVisible = toolbarMainMenu.hasInvisibleItems(expandableMenu)
      if (wasChanged) {
        if (toolbarMainMenu.rootMenuItems.isEmpty()) {
          val item = toolbarMainMenu.pollNextInvisibleItem(expandableMenu)
          toolbarMainMenu.add(item)
        }
        toolbarMainMenu.rootMenuItems.forEach { it.updateUI() }
      }
    }
  }


  fun getButtonIcon(): Icon = if (isMergedMainMenu()) AllIcons.General.ChevronRight else AllIcons.General.WindowsMenu_20x20


  private fun supportKeyNavigationToFullMenu() {
    val selectionManager = MenuSelectionManager.defaultManager()
    val listener: (ChangeEvent) -> Unit = {
      val path = selectionManager.selectedPath
      if (path.size > 0 && path[0] === toolbarMainMenu) {
        val map = frame.rootPane.actionMap
        addAction(map, "selectChild")
        addAction(map, "selectParent")
      }
    }
    selectionManager.addChangeListener(listener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      selectionManager.removeChangeListener(listener)
    }
  }

  private fun addAction(map: ActionMap, name: String) {
    val action = map.get(name)
    if (action is Action && action !is MenuNavigationAction) {
      map.put(name, MenuNavigationAction(name, action, mainMenuButton, toolbarMainMenu))
    }
  }
}

@ApiStatus.Internal
class MergedMainMenu(coroutineScope: CoroutineScope, frame: JFrame): IdeJMenuBar(coroutineScope = coroutineScope, frame = frame) {

  init {
    isOpaque = false
    isVisible = ShowMode.getCurrent() == ShowMode.TOOLBAR_WITH_MENU
    border = null
  }

  private val invisibleItems: ConcurrentLinkedDeque<ActionMenu> = ConcurrentLinkedDeque<ActionMenu>()

  fun clearInvisibleItems() {
    invisibleItems.clear()
  }

  fun addInvisibleItem(item: ActionMenu) {
    invisibleItems.offerLast(item) // Add to removed items (LIFO behavior)
  }

  internal fun pollNextInvisibleItem(expandableMenu: ExpandableMenu?): ActionMenu? {
    val expandableMenuNextItem = expandableMenu?.ideMenu?.rootMenuItems?.getOrNull(rootMenuItems.size) ?: return null
    val lastItem = invisibleItems.last()
    val matchingItem = if (lastItem.text == expandableMenuNextItem?.text) lastItem else invisibleItems.find { it.text == expandableMenuNextItem?.text }
    matchingItem?.let {
      invisibleItems.remove(it)
    }
    return matchingItem

  }

  internal fun hasInvisibleItems(expandableMenu: ExpandableMenu?): Boolean {
    if (expandableMenu == null || invisibleItems.isEmpty()) return false
    return rootMenuItems.size < expandableMenu.ideMenu.rootMenuItems.size && invisibleItems.isNotEmpty()
  }

  fun getInvisibleItemsCount(): Int = invisibleItems.size
}

internal class MenuNavigationAction(
  @NlsSafe val name: String,
  val action: Action,
  val mainMenuButton: MainMenuButton,
  val toolbarMainMenu: MergedMainMenu,
) : AbstractAction(name) {

  override fun actionPerformed(e: ActionEvent) {
    val path = MenuSelectionManager.defaultManager().selectedPath
    if (path.size > 0 && path[0] === toolbarMainMenu) {
      if (name == "selectParent") {
        if (path.size == 4 && path[1] === toolbarMainMenu.getMenu(0)) {
          if (mainMenuButton.expandableMenu?.isEnabled() == true) {
            mainMenuButton.expandableMenu!!.switchState(itemInd = mainMenuButton.expandableMenu!!.ideMenu.rootMenuItems.lastIndex)
          }
          else {
            mainMenuButton.showPopup(ActionToolbar.getDataContextFor(mainMenuButton.button))
          }
          return
        }
      }
      else if (path.size > 3 && path[1] === toolbarMainMenu.rootMenuItems.last()) {
        val element = path.last()
        if (element is ActionMenu && element.itemCount == 0 || element is ActionMenuItem) {
          mainMenuButton.button.click()
          return
        }
      }
    }
    action.actionPerformed(e)
  }
}