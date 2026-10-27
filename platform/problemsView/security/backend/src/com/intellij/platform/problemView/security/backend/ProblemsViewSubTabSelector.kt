// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemView.security.backend

import com.intellij.openapi.application.EDT
import com.intellij.ui.ClientProperty
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.hover.ListHoverListener
import com.intellij.ui.render.RenderingUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
class ProblemsViewSubTabSelector(scope: CoroutineScope, subTabs: List<ProblemsViewSubTab>) : JPanel(BorderLayout()) {

  private val listModel: CollectionListModel<ProblemsViewSubTabItem> =
    CollectionListModel(subTabs.map { ProblemsViewSubTabItem(it.id, it.presentation.value) })
  private val list: SubTabList = SubTabList()

  private val _selectedItemId: MutableStateFlow<@NonNls String?> = MutableStateFlow(null)

  val selectedItemId: StateFlow<@NonNls String?> = _selectedItemId.asStateFlow()

  init {
    background = UIUtil.getPanelBackground()
    add(createScrollPane(), BorderLayout.CENTER)
    // a row keeps its index, because no row is added or removed
    for ((index, subTab) in subTabs.withIndex()) {
      scope.launch(Dispatchers.EDT) {
        subTab.presentation.collect { listModel.setElementAt(ProblemsViewSubTabItem(subTab.id, it), index) }
      }
    }
  }

  /** Clears the selection for `null`, and ignores an unknown [id]. */
  fun select(@NonNls id: String?) {
    ThreadingAssertions.assertEventDispatchThread()
    if (id == null) {
      list.clearSelection()
      return
    }
    val index = indexOf(id)
    if (index < 0) return
    list.selectedIndex = index
    list.ensureIndexIsVisible(index)
  }

  private fun indexOf(@NonNls id: String?): Int = listModel.items.indexOfFirst { it.id == id }

  private fun createScrollPane(): JScrollPane = ScrollPaneFactory.createScrollPane(createListFiller(), true).apply {
    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    isOpaque = false
    viewport.isOpaque = false
  }

  private fun createListFiller(): JPanel = JPanel(BorderLayout()).apply {
    isOpaque = false
    add(list, BorderLayout.NORTH)
  }

  private inner class SubTabList : JBList<ProblemsViewSubTabItem>(listModel) {
    init {
      selectionMode = ListSelectionModel.SINGLE_SELECTION
      cellRenderer = ProblemsViewSubTabItemRenderer()
      background = UIUtil.getPanelBackground()
      border = JBUI.Borders.empty()
      // the selected row looks focused while the user works in the sub-tab
      ClientProperty.put(this, RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true)
      ListHoverListener.DEFAULT.addTo(this)
      // the list also fires this for a programmatic selection
      addListSelectionListener { _selectedItemId.value = selectedValue?.id }
      // the `accessibleContext` field stays null until getAccessibleContext() creates the context
      getAccessibleContext().accessibleName = SecurityProblemsViewBundle.message("security.problems.view.sub.tab.list.accessible.name")
    }
  }
}
