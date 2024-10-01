// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/**
 * Listens for [JList] selection events, and when [CollapsedActionGroup] selected -- substitutes it with children
 * See [expandCollapsableGroupsOnSelection]
 */
@ApiStatus.Internal
class ListListenerCollapsedActionGroupExpander private constructor(
  private val list: JList<AnAction>,
  private val model: DefaultListModel<AnAction>) : ListSelectionListener {
  companion object {
    @JvmStatic
    @RequiresEdt
    fun expandCollapsableGroupsOnSelection(list: JList<AnAction>, model: DefaultListModel<AnAction>, parentDisposable: Disposable) {
      val instance = ListListenerCollapsedActionGroupExpander(list, model)
      list.addListSelectionListener(instance)
      Disposer.register(parentDisposable, Disposable { list.removeListSelectionListener(instance) })
    }
  }

  override fun valueChanged(e: ListSelectionEvent) {
    // Replace collapsable action with children
    val selectedIndex = list.selectedIndex
    val group = list.selectedValue as? CollapsedActionGroup ?: return
    model.remove(selectedIndex)
    model.addAll(selectedIndex, group.getChildren(ActionManager.getInstance()).asList())
    list.removeListSelectionListener(this)
  }
}