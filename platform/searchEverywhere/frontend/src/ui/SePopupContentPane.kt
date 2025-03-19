// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.searchEverywhere.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.SeTargetItemPresentation
import com.intellij.platform.searchEverywhere.SeTextItemPresentation
import com.intellij.platform.searchEverywhere.frontend.providers.actions.SeActionItemPresentationRenderer
import com.intellij.platform.searchEverywhere.frontend.providers.files.SeTargetItemPresentationRenderer
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeSortedResultAddedEvent
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeSortedResultReplacedEvent
import com.intellij.platform.searchEverywhere.frontend.vm.SePopupVm
import com.intellij.platform.searchEverywhere.frontend.vm.SeResultListStopEvent
import com.intellij.platform.searchEverywhere.frontend.vm.SeResultListUpdateEvent
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.bindTextOnShow
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.isWaylandToolkit
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.event.*
import java.util.function.Supplier
import javax.swing.*

@Internal
class SePopupContentPane(private val vm: SePopupVm): JPanel(), Disposable {
  val preferableFocusedComponent: JComponent get() = textField

  private val headerPane: SePopupHeaderPane = SePopupHeaderPane(vm.tabVms.map { it.name }, vm.currentTabIndex, vm.coroutineScope)
  private val textField: SeTextField = SeTextField()

  private val resultListModel = JBList.createDefaultListModel<SeResultListRow>()
  private val resultList: JBList<SeResultListRow> = JBList(resultListModel)
  private val resultsScrollPane = createListPane(resultList)

  init {
    layout = GridLayout()

    val actionListCellRenderer = SeActionItemPresentationRenderer(resultList).get { textField.text ?: "" }
    val fileListCellRenderer = SeTargetItemPresentationRenderer().get()
    val defaultRenderer = listCellRenderer<SeResultListRow> {
      when (val value = value) {
        is SeResultListItemRow -> {
          when (val presentation = value.item.presentation) {
            is SeTextItemPresentation -> text(presentation.text)
            else ->  throw IllegalStateException("Item is not handled: $presentation")
          }
        }
        is SeResultListMoreRow -> text(IdeBundle.message("search.everywhere.points.loading"))
      }
    }

    resultList.setCellRenderer(ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
      if (value is SeResultListItemRow && value.item.presentation is SeActionItemPresentation) {
        actionListCellRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      }
      else if (value is SeResultListItemRow && value.item.presentation is SeTargetItemPresentation) {
        fileListCellRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      }
      else {
        defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
      }
    })

    resultList.setFocusable(false)

    RowsGridBuilder(this)
      .row().cell(headerPane, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row().cell(textField, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row(resizable = true).cell(resultsScrollPane, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL, resizableColumn = true)

    textField.bindTextOnShow(vm.searchPattern, "Search Everywhere text field text binding")

    vm.coroutineScope.launch {
      vm.searchResults.collectLatest { listEventFlow ->
        withContext(Dispatchers.EDT) {
          resultListModel.removeAllElements()
          if (vm.searchPattern.value.isNotEmpty()) {
            resultListModel.addElement(SeResultListMoreRow)
          }
        }

        listEventFlow.collect { listEvent ->
          withContext(Dispatchers.EDT) {
            when (listEvent) {
              is SeResultListUpdateEvent -> {
                when (val sortedEvent = listEvent.event) {
                  is SeSortedResultAddedEvent -> {
                    resultListModel.add(sortedEvent.index, SeResultListItemRow(sortedEvent.itemData))
                  }
                  is SeSortedResultReplacedEvent -> {
                    resultListModel.removeElement(sortedEvent.indexToRemove)
                    resultListModel.add(sortedEvent.index, SeResultListItemRow(sortedEvent.itemData))
                  }
                }
              }

              SeResultListStopEvent -> {
                if (!resultListModel.isEmpty && resultListModel.lastElement() is SeResultListMoreRow) {
                  resultListModel.removeElementAt(resultListModel.size() - 1)
                }
              }
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

    initActions()

    return resultsScroll
  }

  private fun initActions() {
    registerSelectItemAction()
    installScrollingActions()
    initSearchActions()
  }

  // when user adds shortcut for "select item" we should add shortcuts
  // with all possible modifiers (Ctrl, Shift, Alt, etc.)
  private fun registerSelectItemAction() {
    val allowedModifiers = intArrayOf(
      0,
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
      DumbAwareAction.create { _: AnActionEvent? ->
        val indices: IntArray = resultList.selectedIndices
        vm.coroutineScope.launch(Dispatchers.EDT) {
          elementsSelected(indices, modifiers)
        }
      }.registerCustomShortcutSet(newShortcutSet, this, this)
    }
  }

  private suspend fun elementsSelected(indexes: IntArray, modifiers: Int) {
    //stopSearching();

    if (indexes.size == 1 && resultListModel[indexes[0]] is SeResultListMoreRow) {
      return
    }

    val itemDataList = indexes.map {
      resultListModel[it]
    }.mapNotNull {
      (it as? SeResultListItemRow)?.item
    }

    var closePopup = false
    for (itemData in itemDataList) {
      closePopup = closePopup || vm.itemSelected(itemData, modifiers)
    }

    if (closePopup) {
      closePopup()
    }
    else {
      resultList.repaint()
    }
  }

  private fun installScrollingActions() {
    ScrollingUtil.installMoveUpAction(resultList, textField)
    ScrollingUtil.installMoveDownAction(resultList, textField)
  }

  private fun initSearchActions() {
    val listMouseListener: MouseAdapter = object : MouseAdapter() {
      private var currentDescriptionIndex = -1

      override fun mouseClicked(e: MouseEvent) {
        onMouseClicked(e)
      }

      override fun mouseMoved(e: MouseEvent) {
        val index: Int = resultList.locationToIndex(e.point)
        indexChanged(index)
      }

      override fun mouseExited(e: MouseEvent) {
        val index: Int = resultList.selectedIndex
        indexChanged(index)
      }

      private fun indexChanged(index: Int) {
        if (index != currentDescriptionIndex) {
          currentDescriptionIndex = index
          showDescriptionForIndex()
        }
      }
    }

    resultList.addMouseMotionListener(listMouseListener)
    resultList.addMouseListener(listMouseListener)

    ScrollingUtil.redirectExpandSelection(resultList, textField)

    val nextTabAction: (AnActionEvent) -> Unit = { _ ->
      vm.selectNextTab()
    }
    val prevTabAction: (AnActionEvent) -> Unit = { _ ->
      vm.selectPreviousTab()
    }

    registerAction(SeActions.SWITCH_TO_NEXT_TAB, nextTabAction)
    registerAction(SeActions.SWITCH_TO_PREV_TAB, prevTabAction)
    registerAction(IdeActions.ACTION_NEXT_TAB, nextTabAction)
    registerAction(IdeActions.ACTION_PREVIOUS_TAB, prevTabAction)
    registerAction(IdeActions.ACTION_SWITCHER) { e ->
      if (e.inputEvent?.isShiftDown == true) {
        vm.selectPreviousTab()
      }
      else {
        vm.selectNextTab()
      }
    }
    registerAction(SeActions.NAVIGATE_TO_NEXT_GROUP) { _ ->
      shiftSelectedIndexAndEnsureIsVisible(1)
      vm.usageLogger.groupNavigate()
    }
    registerAction(SeActions.NAVIGATE_TO_PREV_GROUP) { _ ->
      shiftSelectedIndexAndEnsureIsVisible(-1)
      vm.usageLogger.groupNavigate()
    }

    val escape = ActionManager.getInstance().getAction("EditorEscape")
    DumbAwareAction.create { closePopup() }
      .registerCustomShortcutSet(escape?.shortcutSet ?: CommonShortcuts.ESCAPE, this)

    textField.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        onFocusLost()
      }
    })
  }

  private fun shiftSelectedIndexAndEnsureIsVisible(shift: Int) {
    val currentIndex: Int = resultList.selectedIndex
    val newIndex: Int = (currentIndex + shift).coerceIn(0, resultList.model.size - 1)

    if (newIndex != currentIndex) {
      resultList.selectedIndex = newIndex
      ScrollingUtil.ensureIndexIsVisible(resultList, newIndex, 0)
    }
  }

  private fun onMouseClicked(e: MouseEvent) {
    val multiSelectMode = e.isShiftDown || UIUtil.isControlKeyDown(e)
    //val isPreviewDoubleClick = !SearchEverywhereUI.isPreviewActive() || !SearchEverywhereUI.hasPreviewProvider(myHeader.getSelectedTab()) || e.clickCount == 2

    if (e.button == MouseEvent.BUTTON1 && !multiSelectMode) {
      e.consume()
      val i: Int = resultList.locationToIndex(e.point)
      if (i > -1) {
        resultList.setSelectedIndex(i)
        val modifiers = e.modifiersEx

        vm.coroutineScope.launch {
          withContext(Dispatchers.EDT) {
            elementsSelected(intArrayOf(i), modifiers)
          }
        }
      }
    }
  }

  private fun registerAction(actionID: String, actionSupplier: Supplier<out AnAction>) {
    val anAction = ActionManager.getInstance().getAction(actionID) ?: return
    val shortcuts = anAction.shortcutSet
    actionSupplier.get().registerCustomShortcutSet(shortcuts, this, this)
  }

  private fun registerAction(actionID: String, action: (AnActionEvent) -> Unit) {
    registerAction(actionID, Supplier<AnAction> {
      DumbAwareAction.create(action)
    })
  }

  private fun showDescriptionForIndex() {
    // TODO: Implement description footer
  }

  private fun onFocusLost() {
    if (isWaylandToolkit()) {
      // In Wayland focus is always lost when the window is being moved.
      return
    }
    if (ApplicationManagerEx.isInIntegrationTest()) {
      return
    }

    closePopup()
  }

  private fun closePopup() {
    vm.closePopup()
  }

  override fun dispose() { }
}
