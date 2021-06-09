// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.cloneDialog

import com.intellij.ui.DocumentAdapter
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.SingleSelectionModel
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import javax.swing.ListCellRenderer
import javax.swing.ListModel
import javax.swing.event.DocumentEvent

@Deprecated("Replaced with simpler and more flexible version [com.intellij.collaboration.ui.CollaborationToolsUIUtil.attachSearch]")
class ListWithSearchComponent<T : SearchableListItem>(
  originModel: ListModel<T>,
  listCellRenderer: ListCellRenderer<T>
) {
  private val speedSearch: SpeedSearch = SpeedSearch(false)

  private val filteringListModel: NameFilteringListModel<T> = NameFilteringListModel(
    originModel,
    { param -> param.stringToSearch },
    speedSearch::shouldBeShowing,
    { speedSearch.filter ?: "" }
  )

  val list: JBList<T> = JBList(filteringListModel).apply {
    cellRenderer = listCellRenderer
    isFocusable = false
    selectionModel = SingleSelectionModel()
  }

  val searchField: SearchTextField = SearchTextField(false)

  init {
    searchField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) = speedSearch.updatePattern(searchField.text)
    })

    speedSearch.addChangeListener {
      val prevSelection = list.selectedValue // save to restore the selection on filter drop
      filteringListModel.refilter()
      if (filteringListModel.size > 0) {
        val fullMatchIndex = if (speedSearch.isHoldingFilter) filteringListModel.closestMatchIndex
        else filteringListModel.getElementIndex(prevSelection)
        if (fullMatchIndex != -1) {
          list.selectedIndex = fullMatchIndex
        }

        if (filteringListModel.size <= list.selectedIndex || !filteringListModel.contains(list.selectedValue)) {
          list.selectedIndex = 0
        }
      }
    }

    ScrollingUtil.installActions(list)
    ScrollingUtil.installActions(list, searchField.textEditor)
  }
}

@Deprecated("Replaced with simpler and more flexible version [com.intellij.collaboration.ui.CollaborationToolsUIUtil.attachSearch]")
interface SearchableListItem {
  val stringToSearch: String?
}