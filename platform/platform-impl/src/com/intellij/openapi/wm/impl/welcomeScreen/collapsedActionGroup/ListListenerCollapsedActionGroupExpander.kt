// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JList

/**
 * Listens for [JList] clicks events, and when [CollapsedActionGroup] clicked,
 * adds children to model on expanding or removes them on collapse.
 */
@ApiStatus.Internal
class ListListenerCollapsedActionGroupExpander private constructor(
  private val list: JList<AnAction>,
  private val model: DefaultListModel<AnAction>,
) : MouseAdapter() {

  companion object {
    @JvmStatic
    @RequiresEdt
    fun expandCollapsableGroupsOnClick(list: JList<AnAction>, model: DefaultListModel<AnAction>) {
      val instance = ListListenerCollapsedActionGroupExpander(list, model)
      list.addMouseListener(instance)
    }
  }

  /** Returns model index of nearest ActionGroup in given direction from startIndex.*/
  private fun getNotGroupIndexInDirection(startIndex: Int, direction: Int): Int {
    var i = startIndex + direction
    while (i >= 0 && i < model.size) {
      if (model.getElementAt(i) !is ActionGroup) {
        return i
      }
      i += direction
    }
    return startIndex
  }

  override fun mouseClicked(e: MouseEvent) {
    val clickedIndex = list.locationToIndex(e.getPoint())

    val group = model.getElementAt(clickedIndex) as? CollapsedActionGroup ?: return

    val children = group.getChildren(ActionManager.getInstance())

    val groupCollapsed = children.firstOrNull { !model.contains(it) } != null
    if (groupCollapsed) {
      model.addAll(clickedIndex + 1, children.asList())
    }
    else {
      // If selection was in collapsed region, moving selection out of it.
      val selectedIndexInCollapsedRegion = children.firstOrNull { model.indexOf(it) == list.selectedIndex } != null
      if (selectedIndexInCollapsedRegion) {
        val firstTry = getNotGroupIndexInDirection(clickedIndex, -1)
        if (firstTry != clickedIndex) {
          list.selectedIndex = firstTry
        }
      }

      model.removeRange(clickedIndex + 1, clickedIndex+children.size)
    }
  }
}