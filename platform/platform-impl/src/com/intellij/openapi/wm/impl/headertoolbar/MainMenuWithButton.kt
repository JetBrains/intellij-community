// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.headertoolbar

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.UiComponentsSearchUtil
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
import com.intellij.ui.util.width
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.ConcurrentHashMap
import javax.swing.*
import javax.swing.event.ChangeEvent
import kotlin.math.roundToInt

private val LOG = Logger.getInstance(MainMenuWithButton::class.java)

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
  private var recalculateWidthJob: Job? = null

  fun recalculateWidth(toolbar: MainToolbar?) {
    val isMergedMenu = isMergedMainMenu()
    toolbarMainMenu.isVisible = isMergedMenu
    mainMenuButton.button.presentation.icon = getButtonIcon()
    val expandableMenu = mainMenuButton.expandableMenu
    mainMenuButton.button.isVisible = !isMergedMenu || toolbarMainMenu.hasInvisibleItems(expandableMenu)

    if (!isMergedMenu) return

    val prevJob = recalculateWidthJob
    recalculateWidthJob = coroutineScope.launch(Dispatchers.EDT) {
      prevJob?.join()
      var wasChanged = false
      if (toolbarMainMenu.rootMenuItems.isEmpty() && toolbarMainMenu.hasInvisibleItems(expandableMenu)) {
        toolbarMainMenu.getNextInvisibleItem(expandableMenu)?.let { itemToWidth ->
          toolbarMainMenu.add(itemToWidth.first)
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
          toolbarMainMenu.addInvisibleItem(item)
          toolbarMainMenu.remove(item)
          widthToFree -= item.size.width
          wasChanged = true
        }
      }
      else if (availableWidth > widthLimit) {
        while (availableWidth > widthLimit && toolbarMainMenu.hasInvisibleItems(expandableMenu)) {
          val itemToWidth = toolbarMainMenu.getNextInvisibleItem(expandableMenu) ?: break
          val item = itemToWidth.first
          val itemWidth = itemToWidth.second
          if (availableWidth - itemWidth < widthLimit) {
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
          toolbarMainMenu.getNextInvisibleItem(expandableMenu)?.let {
            toolbarMainMenu.add(it.first)
          }
        }
        toolbarMainMenu.rootMenuItems.forEach { it.updateUI() }
        toolbarMainMenu.revalidate()
        toolbarMainMenu.repaint()
      }
    }
  }

  fun recalculateWidth() {
    val mainToolbar = UiComponentsSearchUtil.findUiComponent(frame) { _: MainToolbar -> true }
    if (mainToolbar == null) {
      LOG.info("Main toolbar not found for recalculation of the menu width")
      return
    }
    recalculateWidth(mainToolbar)
  }

  fun getButtonIcon(): Icon = if (isMergedMainMenu()) AllIcons.General.ChevronRight else AllIcons.General.WindowsMenu_20x20


  private fun supportKeyNavigationToFullMenu() {
    val selectionManager = MenuSelectionManager.defaultManager()
    val listener: (ChangeEvent) -> Unit = {
      val path = selectionManager.selectedPath
      if (path.size > 0 && path[0] === toolbarMainMenu) {
        val map = frame.rootPane.actionMap
        addAction(map, MenuNavigationAction.SELECT_CHILD)
        addAction(map, MenuNavigationAction.SELECT_PARENT)
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
class MergedMainMenu(coroutineScope: CoroutineScope, frame: JFrame) : IdeJMenuBar(coroutineScope = coroutineScope, frame = frame) {

  /** Cache the item's width as its real size cannot be obtained when it is not painted.
  item.preferredSize often twice bigger than the real one (when the menu item is not painted)
  preferred size computed by ui class is also often +20px
   */
  private val invisibleItems: ConcurrentHashMap<String, Pair<ActionMenu, Int>> = ConcurrentHashMap()

  init {
    isOpaque = false
    isVisible = ShowMode.getCurrent() == ShowMode.TOOLBAR_WITH_MENU
    border = null
    val userScaleListener = object : PropertyChangeListener {
      override fun propertyChange(evt: PropertyChangeEvent) {
        val oldScale = evt.oldValue as? Float ?: return
        val newScale = evt.newValue as? Float ?: return
        if (oldScale == newScale || invisibleItems.isEmpty()) return
        invisibleItems.forEach { (name, itemToWidth) ->
          val width = (itemToWidth.second / oldScale * newScale).roundToInt()
          invisibleItems.put(name, Pair(itemToWidth.first, width))
        }
      }
    }
    JBUIScale.addUserScaleChangeListener(userScaleListener)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      JBUIScale.removeUserScaleChangeListener(userScaleListener)
    }
  }

  fun addInvisibleItem(item: ActionMenu) {
    val name = item.text
    var width = item.size.width
    if (width == 0) {
      invisibleItems[name]?.second?.let {
        width = it
      }
      if (width == 0) {
        val fontMetrics = item.getFontMetrics(item.font)
        width = fontMetrics.stringWidth(item.text) + item.insets.width
      }
    }
    invisibleItems.put(name, Pair(item, width))
  }

  internal fun getNextInvisibleItem(expandableMenu: ExpandableMenu?): Pair<ActionMenu, Int>? {
    val expandableMenuItems = expandableMenu?.ideMenu?.rootMenuItems
    val expandableMenuNextItem = expandableMenuItems?.getOrNull(rootMenuItems.size)
    if (expandableMenuNextItem == null) {
      if (expandableMenu == null) LOG.warn("expandable menu is null, couldn't be used for merged menu next item calculation")
      else {
        LOG.warn("Trying to get expandable menu item with index ${rootMenuItems.size} to calculate next merged menu item, " +
                 "but expandable menu size = ${expandableMenuItems?.size}")
        expandableMenu.ideMenu.updateMenuActions(true)
      }
      return null
    }
    val nextItemText = expandableMenuNextItem.text
    if (rootMenuItems.any { it.text == nextItemText}) {
      LOG.warn("Invisible item already added: ${nextItemText}. Run update menu actions")
      this.updateMenuActions(true)
      return null
    }
    val itemToWidth = invisibleItems[nextItemText]
    if (itemToWidth == null) {
      LOG.warn("Invisible item not found: ${nextItemText}. Run update menu actions")
      expandableMenu.ideMenu.updateMenuActions(true)
      this.updateMenuActions(true)
    }

    return itemToWidth
  }

  internal fun hasInvisibleItems(expandableMenu: ExpandableMenu?): Boolean {
    if (expandableMenu == null) {
      LOG.warn("expandable menu is null, couldn't be used for check is merged menu has invisible items")
      return false
    }
    val menuItemCount = rootMenuItems.size
    val expandableMenuItemCount = expandableMenu.ideMenu.rootMenuItems.size
    if (menuItemCount == expandableMenuItemCount) return false
    if (menuItemCount > expandableMenuItemCount || invisibleItems.isEmpty()) {
      LOG.warn("Invisible items count mismatch: expandableMenuItemCount = $expandableMenuItemCount,  menuItemCount = $menuItemCount, invisibleItems is empty = ${invisibleItems.isEmpty()}. Run update menu actions.")
      expandableMenu.ideMenu.updateMenuActions(true)
      this.updateMenuActions(true)
      return false
    }
    return true
  }
}

internal class MenuNavigationAction(
  @NlsSafe val name: String,
  val action: Action,
  val mainMenuButton: MainMenuButton,
  val toolbarMainMenu: MergedMainMenu,
) : AbstractAction(name) {

  companion object {
    const val SELECT_CHILD = "selectChild"
    const val SELECT_PARENT = "selectParent"
  }

  override fun actionPerformed(e: ActionEvent) {
    val path = MenuSelectionManager.defaultManager().selectedPath
    if (path.size > 0 && path[0] === toolbarMainMenu) {
      if (name == SELECT_PARENT) {
        // if we try to navigate to previous element before first item we just expand full menu and select last element
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
      // if we try to navigate to next element after last item we just expand full menu
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