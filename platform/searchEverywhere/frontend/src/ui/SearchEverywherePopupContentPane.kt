// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.frontend.vm.SearchEverywherePopupVm
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.bindTextIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent
import javax.swing.JPanel

@Internal
class SearchEverywherePopupContentPane(private val vm: SearchEverywherePopupVm): JPanel(), Disposable {
  val preferableFocusedComponent: JComponent get() = textField

  private val tabsPane: SearchEverywhereTabsPane = SearchEverywhereTabsPane(vm.tabVms.map { it.name })
  private val textField: SearchEverywhereTextField = SearchEverywhereTextField()

  private val resultListModel = JBList.createDefaultListModel<SearchEverywhereItemData>()
  private val resultList: JBList<SearchEverywhereItemData> = JBList(resultListModel)
  private val resultsScrollPane = JBScrollPane(resultList)

  init {
    layout = GridLayout()

    resultList.setCellRenderer(listCellRenderer {
      text(value.presentation.text)
    })

    RowsGridBuilder(this)
      .row().cell(tabsPane, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row().cell(textField, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row(resizable = true).cell(resultsScrollPane, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)

    textField.bindTextIn(vm.searchPattern, vm.coroutineScope)

    //tabsPane.tabbedPane.addChangeListener { change ->
    //  change.source
    //}

    vm.coroutineScope.launch {
      vm.searchResults.collectLatest {
        withContext(Dispatchers.EDT) {
          resultListModel.removeAllElements()
        }

        it.collect {
          withContext(Dispatchers.EDT) {
            resultListModel.addElement(it)
          }
        }
      }
    }
  }

  override fun dispose() {
    vm.dispose()
  }
}

