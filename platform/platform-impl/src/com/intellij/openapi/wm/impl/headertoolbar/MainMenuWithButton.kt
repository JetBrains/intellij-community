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
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ActionEvent
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
        toolbarMainMenu.pollNextInvisibleItem(expandableMenu)?.let { itemToWidth ->
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
          val itemToWidth = toolbarMainMenu.pollNextInvisibleItem(expandableMenu) ?: break
          val item = itemToWidth.first
          val itemWidth = itemToWidth.second
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
          toolbarMainMenu.pollNextInvisibleItem(expandableMenu)?.let {
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
    JBUIScale.addUserScaleChangeListener {
      val oldScale = it.oldValue as? Float ?: return@addUserScaleChangeListener
      val newScale = it.newValue as? Float ?: return@addUserScaleChangeListener
      if (oldScale == newScale || invisibleItems.isEmpty()) return@addUserScaleChangeListener
      invisibleItems.forEach {
        (name, itemToWidth) ->
        val width = (itemToWidth.second / oldScale * newScale).roundToInt()
        invisibleItems.put(name, Pair(itemToWidth.first, width))
      } }
  }


  fun clearInvisibleItems() {
    invisibleItems.clear()
  }

  fun addInvisibleItem(item: ActionMenu) {
    val name = item.text
    val width = item.size.width
    invisibleItems.put(name, Pair(item, width))
  }

  internal fun pollNextInvisibleItem(expandableMenu: ExpandableMenu?): Pair<ActionMenu, Int>? {
    val expandableMenuNextItem = expandableMenu?.ideMenu?.rootMenuItems?.getOrNull(rootMenuItems.size) ?: return null
    val itemToWidth = invisibleItems[expandableMenuNextItem.text] ?: return null
    return itemToWidth
  }

  internal fun hasInvisibleItems(expandableMenu: ExpandableMenu?): Boolean {
    if (expandableMenu == null || invisibleItems.isEmpty()) return false
    return rootMenuItems.size < expandableMenu.ideMenu.rootMenuItems.size && invisibleItems.isNotEmpty()
  }
}