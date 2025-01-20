// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.searchEverywhere.frontend.vm.SeListItemData
import com.intellij.platform.searchEverywhere.frontend.vm.SePopupVm
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.bindTextIn
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.event.InputEvent
import javax.swing.*

@Internal
class SePopupContentPane(private val vm: SePopupVm, private val popupManager: SePopupManager): JPanel(), Disposable {
  val preferableFocusedComponent: JComponent get() = textField

  private val headerPane: SePopupHeaderPane = SePopupHeaderPane(vm.tabVms.map { it.name }, vm.currentTabIndex, vm.coroutineScope)
  private val textField: SeTextField = SeTextField()

  private val resultListModel = JBList.createDefaultListModel<SeResultListRow>()
  private val resultList: JBList<SeResultListRow> = JBList(resultListModel)
  private val resultsScrollPane = createListPane(resultList)

  init {
    layout = GridLayout()

    resultList.setCellRenderer(listCellRenderer {
      when (val value = value) {
        is SeResultListItemRow -> text(value.item.presentation.text)
        is SeResultListMoreRow -> text(IdeBundle.message("search.everywhere.points.loading"))
      }
    })

//    registerSelectItemAction

    RowsGridBuilder(this)
      .row().cell(headerPane, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row().cell(textField, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row(resizable = true).cell(resultsScrollPane, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL, resizableColumn = true)

    textField.bindTextIn(vm.searchPattern, vm.coroutineScope)

    vm.coroutineScope.launch {
      vm.searchResults.collectLatest { resultsFlow ->
        withContext(Dispatchers.EDT) {
          resultListModel.removeAllElements()
        }

        resultsFlow.collect { listItem ->
          withContext(Dispatchers.EDT) {
            if (!resultListModel.isEmpty && resultListModel.lastElement() is SeResultListMoreRow) {
              resultListModel.removeElementAt(resultListModel.size() - 1)
            }

            (listItem as? SeListItemData)?.let { it ->
              resultListModel.addElement(SeResultListItemRow(it.value))
              resultListModel.addElement(SeResultListMoreRow)
            }
          }
        }
      }
    }

    vm.coroutineScope.launch {
      vm.currentTabFlow.collectLatest {
        withContext(Dispatchers.EDT) {
          headerPane.setFilterComponent(it.filterEditor?.component)
        }
      }
    }

    val verticalScrollBar = resultsScrollPane.verticalScrollBar
    verticalScrollBar.addAdjustmentListener { adjustmentEvent ->
      val yetToScrollHeight = verticalScrollBar.maximum - verticalScrollBar.model.extent - adjustmentEvent.value

      if (verticalScrollBar.model.extent > 0 && yetToScrollHeight < 50) {
        vm.shouldLoadMore = true
      } else if (yetToScrollHeight > resultsScrollPane.height / 2) {
        vm.shouldLoadMore = false
      }
    }
  }

  private fun createListPane(resultList: JBList<*>): JScrollPane {
    val resultsScroll: JScrollPane = object : JBScrollPane(resultList) {
      override fun updateUI() {
        val isBorderNull = border == null
        super.updateUI()
        if (isBorderNull) border = null
      }
    }
    resultsScroll.border = null
    resultsScroll.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    resultsScroll.verticalScrollBar.putClientProperty(JBScrollPane.IGNORE_SCROLLBAR_IN_INSETS, true)
    resultsScroll.background = JBUI.CurrentTheme.Popup.BACKGROUND
    resultList.background = JBUI.CurrentTheme.Popup.BACKGROUND

    resultsScroll.preferredSize = JBUI.size(670, JBUI.CurrentTheme.BigPopup.maxListHeight())

    initSearchActions()

    return resultsScroll
  }

  private fun initSearchActions() {
    registerSelectItemAction()
  }

  // when user adds shortcut for "select item" we should add shortcuts
  // with all possible modifiers (Ctrl, Shift, Alt, etc.)
  private fun registerSelectItemAction() {
    val allowedModifiers = intArrayOf(0,
                                      InputEvent.SHIFT_DOWN_MASK,
                                      InputEvent.CTRL_DOWN_MASK,
                                      InputEvent.META_DOWN_MASK,
                                      InputEvent.ALT_DOWN_MASK
    )

    val selectShortcuts = ActionManager.getInstance().getAction(SeActions.SELECT_ITEM).shortcutSet
    val keyboardShortcuts: Collection<KeyboardShortcut> = ContainerUtil.filterIsInstance(selectShortcuts.shortcuts, KeyboardShortcut::class.java)

    for (modifiers in allowedModifiers) {
      val newShortcuts: MutableCollection<Shortcut> = ArrayList()
      for (shortcut in keyboardShortcuts) {
        val hasSecondStroke = shortcut.secondKeyStroke != null
        val originalStroke = if (hasSecondStroke) shortcut.secondKeyStroke!! else shortcut.firstKeyStroke

        if ((originalStroke.modifiers and modifiers) != 0) continue

        val newStroke = KeyStroke.getKeyStroke(originalStroke.keyCode, originalStroke.modifiers or modifiers)
        newShortcuts.add(if (hasSecondStroke)
                           KeyboardShortcut(shortcut.firstKeyStroke, newStroke)
                         else
                           KeyboardShortcut(newStroke, null))
      }
      if (newShortcuts.isEmpty()) continue



      val newShortcutSet: ShortcutSet = CustomShortcutSet(*newShortcuts.toTypedArray())
      DumbAwareAction.create { event: AnActionEvent? ->
        val indices: IntArray = resultList.selectedIndices
        vm.coroutineScope.launch {
          withContext(Dispatchers.EDT) {
            elementsSelected(indices, modifiers)
          }
        }
      }.registerCustomShortcutSet(newShortcutSet, this, this)
    }
  }

  private suspend fun elementsSelected(indexes: IntArray, modifiers: Int) {
    //stopSearching();

    if (indexes.size == 1 && resultListModel[indexes[0]] is SeResultListMoreRow) {
      return;
    }

    val itemDataList = indexes.map {
      resultListModel[it]
    }.mapNotNull {
      (it as? SeResultListItemRow)?.item
    }

    var closePopup = false;
    for (itemData in itemDataList) {
      closePopup = closePopup || vm.itemSelected(itemData, modifiers);
    }

    if (closePopup) {
      closePopup();
    }
    else {
      withContext(Dispatchers.EDT) {
        resultList.repaint()
      }
    }
  }

  private fun closePopup() {
    popupManager.closePopup()
  }

  override fun dispose() {
    vm.dispose()
  }
}

@Internal
interface SePopupManager {
  fun closePopup()
}
