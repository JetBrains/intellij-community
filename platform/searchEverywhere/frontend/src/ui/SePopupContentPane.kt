// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.accessibility.TextFieldWithListAccessibleContext
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.ExtendedInfo
import com.intellij.ide.actions.searcheverywhere.HintHelper
import com.intellij.ide.actions.searcheverywhere.footer.ExtendedInfoComponent
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.searchEverywhere.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.SeTargetItemPresentation
import com.intellij.platform.searchEverywhere.SeTextSearchItemPresentation
import com.intellij.platform.searchEverywhere.frontend.AutoToggleAction
import com.intellij.platform.searchEverywhere.frontend.tabs.actions.SeActionItemPresentationRenderer
import com.intellij.platform.searchEverywhere.frontend.tabs.files.SeTargetItemPresentationRenderer
import com.intellij.platform.searchEverywhere.frontend.tabs.text.SeTextSearchItemPresentationRenderer
import com.intellij.platform.searchEverywhere.frontend.vm.SePopupVm
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.ui.*
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.bindTextIn
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.isWaylandToolkit
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.*
import java.util.function.Supplier
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.text.Document
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.roundToInt

@OptIn(ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class)
@Internal
class SePopupContentPane(private val project: Project?, private val vm: SePopupVm,
                         private val resizePopupHandler: (Dimension) -> Unit,
                         initPopupExtendedSize: Dimension?,
                         onShowFindToolWindow: () -> Unit) : JPanel(), Disposable, UiDataProvider {
  val preferableFocusedComponent: JComponent get() = textField
  val searchFieldDocument: Document get() = textField.document

  private val headerPane: SePopupHeaderPane = SePopupHeaderPane(
    project,
    vm.tabVms.map { SePopupHeaderPane.Tab(it) },
    vm.currentTabIndex,
    vm.coroutineScope,
    vm.ShowInFindToolWindowAction(onShowFindToolWindow)
  )

  private val textField = object : SeTextField() {
    override fun getAccessibleContext(): AccessibleContext {
      if (accessibleContext == null) {
        accessibleContext = TextFieldWithListAccessibleContext(this, resultList.getAccessibleContext())
      }
      return accessibleContext
    }
  }
  private val hintHelper = HintHelper(textField)

  private val resultListModel = SeResultListModel { resultList.selectionModel }
  private val resultList: JBList<SeResultListRow> = JBList(resultListModel)
  private val resultsScrollPane = createListPane(resultList)

  private val extendedInfoContainer: JComponent = JPanel(BorderLayout())
  private var extendedInfoComponent: ExtendedInfoComponent? = null

  private val isSearchCompleted: AtomicBoolean = AtomicBoolean(false)

  var isCompactViewMode: Boolean = true
    private set
  var popupExtendedSize: Dimension? = initPopupExtendedSize

  private val minWidth = Registry.intValue("search.everywhere.new.minimum.width", 700)

  init {
    layout = GridLayout()

    val actionListCellRenderer = SeActionItemPresentationRenderer(resultList).get { textField.text ?: "" }
    val targetListCellRenderer = SeTargetItemPresentationRenderer(resultList).get()
    val textSearchItemListCellRenderer = SeTextSearchItemPresentationRenderer().get()
    val defaultRenderer = SeDefaultListItemRenderer().get()

    ClientProperty.put(resultList, AnimatedIcon.ANIMATION_IN_RENDERER_ALLOWED, true)
    resultList.setCellRenderer(ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
      when (value) {
        is SeResultListItemRow if value.item.presentation is SeActionItemPresentation -> {
          actionListCellRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
        is SeResultListItemRow if value.item.presentation is SeTargetItemPresentation -> {
          targetListCellRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
        is SeResultListItemRow if value.item.presentation is SeTextSearchItemPresentation -> {
          textSearchItemListCellRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
        else -> {
          defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
      }
    })

    resultList.setFocusable(false)

    updateExtendedInfoContainer()

    RowsGridBuilder(this)
      .row().cell(headerPane, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row().cell(textField, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row(resizable = true).cell(resultsScrollPane, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL, resizableColumn = true)
      .row().cell(extendedInfoContainer, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)

    // hide resultsScrollPane and extendedInfoContainer
    updateViewMode()

    textField.launchOnShow("Search Everywhere text field text binding") {
      withContext(Dispatchers.EDT) {
        textField.text = vm.searchPattern.value
        textField.selectAll()
      }
      textField.bindTextIn(vm.searchPattern, this)
    }

    addHistoryExtensionToTextField()

    vm.coroutineScope.launch {
      vm.currentTabFlow.flatMapLatest {
        withContext(Dispatchers.EDT) {
          resultListModel.reset()
        }
        it.searchResults.filterNotNull()
      }.collectLatest { throttledResultEventFlow ->
        coroutineScope {
          withContext(Dispatchers.EDT) {
            isSearchCompleted.store(false)
            resultListModel.invalidate()

            if (vm.searchPattern.value.isNotEmpty()) {
              hintHelper.setSearchInProgress(true)
            }
          }

          launch {
            delay(DEFAULT_FREEZING_DELAY_MS)
            withContext(Dispatchers.EDT) {
              resultListModel.freezer.enable()
            }
          }

          throttledResultEventFlow.onCompletion {
            withContext(Dispatchers.EDT) {
              SeLog.log(SeLog.THROTTLING) { "Throttled flow completed" }
              isSearchCompleted.store(true)
              resultListModel.removeLoadingItem()

              if (!resultListModel.isValid || resultListModel.isEmpty) {
                if (!textField.text.isEmpty() &&
                    (vm.currentTab.getSearchEverywhereToggleAction() as? AutoToggleAction)?.autoToggle(true) ?: false) {
                  headerPane.updateActionsAsync()
                  return@withContext
                }
              }

              if (!resultListModel.isValid) resultListModel.reset()

              if (resultListModel.isEmpty) {
                hintHelper.setSearchInProgress(false)
                updateEmptyStatus()
              }

              updateViewMode()
            }
          }.collect { event ->
            withContext(Dispatchers.EDT) {
              hintHelper.setSearchInProgress(false)
              val wasFrozen = resultListModel.freezer.isEnabled

              resultListModel.addFromThrottledEvent(event)

              // Freeze back if it was frozen before
              if (wasFrozen) resultListModel.freezer.enable()
              updateFrozenCount()

              // Autoselect the first element if there were no selection preserved during the update
              if (resultListModel.size > 0 && resultList.selectedIndices.isEmpty()) {
                resultList.selectedIndex = 0
              }

              updateViewMode()
            }
          }
        }
      }
    }

    vm.coroutineScope.launch {
      vm.currentTabFlow.collectLatest {
        val filterEditor = it.filterEditor.getValue()
        filterEditor?.let { filterEditor ->
          withContext(Dispatchers.EDT) {
            headerPane.setFilterActions(filterEditor.getActions())
          }
        }
      }
    }

    vm.coroutineScope.launch {
      vm.deferredTabVms.collect { tabVm ->
        withContext(Dispatchers.EDT) {
          headerPane.addTab(SePopupHeaderPane.Tab(tabVm))
        }
      }
    }

    val isScrolledAlmostToAnEnd = MutableStateFlow(false)
    val verticalScrollBar = resultsScrollPane.verticalScrollBar
    verticalScrollBar.addAdjustmentListener { adjustmentEvent ->
      updateFrozenCount()

      val yetToScrollHeight = verticalScrollBar.maximum - verticalScrollBar.model.extent - adjustmentEvent.value
      if (verticalScrollBar.model.extent > 0 && yetToScrollHeight < maxOf(resultsScrollPane.height / 2, 50)) {
        isScrolledAlmostToAnEnd.value = true
      }
      else if (yetToScrollHeight > resultsScrollPane.height * 1.5) {
        isScrolledAlmostToAnEnd.value = false
      }
    }

    vm.coroutineScope.launch {
      vm.currentTabFlow.collectLatest { tabVm ->
        coroutineScope {
          combine(isScrolledAlmostToAnEnd, resultListModel.isValidState) {
            it[0] to it[1]
          }.collect { (isScrolledAlmostToAnEnd, isValidList) ->
            tabVm.shouldLoadMore = isScrolledAlmostToAnEnd || !isValidList
          }
        }
      }
    }

    vm.coroutineScope.launch {
      vm.searchFieldWarning.collect { warning ->
        withContext(Dispatchers.EDT) {
          hintHelper.setWarning(warning)
        }
      }
    }

    WindowMoveListener(this).installTo(headerPane)

    DumbAwareAction.create { vm.getHistoryItem(true)?.let { textField.text = it; textField.selectAll() } }
      .registerCustomShortcutSet(SearchTextField.SHOW_HISTORY_SHORTCUT, this)
    DumbAwareAction.create { vm.getHistoryItem(false)?.let { textField.text = it; textField.selectAll() } }
      .registerCustomShortcutSet(SearchTextField.ALT_SHOW_HISTORY_SHORTCUT, this)
  }

  private fun updateFrozenCount() {
    // All rows above the visible rect plus DEFAULT_FROZEN_VISIBLE_PART of the visible rect
    val indexToFreezeFromListOffset = ((resultsScrollPane.verticalScrollBar.value.toDouble() +
                                        resultsScrollPane.height * DEFAULT_FROZEN_VISIBLE_PART) / JBUI.CurrentTheme.List.rowHeight()).roundToInt()

    resultListModel.freezer.freezeIfEnabled(indexToFreezeFromListOffset)
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

    resultsScroll.preferredSize = JBUI.size(minWidth, JBUI.CurrentTheme.BigPopup.maxListHeight())

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
    var nonItemDataCount = 0

    // Calculate items with indexes considering some non-item rows on top (for example, notification row).
    // The index is necessary for event logging
    val itemDataList = indexes.map {
      it to resultListModel[it]
    }.mapNotNull { (originalIndex, row) ->
      if (row is SeResultListItemRow) {
        (originalIndex - nonItemDataCount) to row.item
      }
      else {
        nonItemDataCount++
        null
      }
    }

    if (vm.itemsSelected(itemDataList, nonItemDataCount == 0, modifiers)) {
      closePopup()
    }
    else {
      resultList.repaint()
    }
  }

  private fun installScrollingActions() {
    val moveUpAction = MoveUpAction()
    moveUpAction.registerCustomShortcutSet(
      CommonShortcuts.getMoveUp(),
      textField
    )

    ScrollingUtil.installMoveDownAction(resultList, textField)

    resultList.addListSelectionListener { _: ListSelectionEvent ->
      val index = resultList.selectedIndex
      if (index != -1) {
        extendedInfoComponent?.updateElement(resultList.selectedValue, this@SePopupContentPane)
      }
    }
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

    val nextTabAction: (AnActionEvent) -> Unit = { e ->
      vm.selectNextTab()
      logTabSwitchedEvent(e)
      updateExtendedInfoContainer()
    }
    val prevTabAction: (AnActionEvent) -> Unit = { e ->
      vm.selectPreviousTab()
      logTabSwitchedEvent(e)
      updateExtendedInfoContainer()
    }

    registerAction(SeActions.SWITCH_TO_NEXT_TAB, nextTabAction)
    registerAction(SeActions.SWITCH_TO_PREV_TAB, prevTabAction)
    registerAction(IdeActions.ACTION_NEXT_TAB, nextTabAction)
    registerAction(IdeActions.ACTION_PREVIOUS_TAB, prevTabAction)
    registerAction(IdeActions.ACTION_SWITCHER) { e ->
      if (e.inputEvent?.isShiftDown == true) {
        prevTabAction(e)
      }
      else {
        nextTabAction(e)
      }
    }
    registerAction(SeActions.NAVIGATE_TO_NEXT_GROUP) { _ ->
      scrollList(true)
    }
    registerAction(SeActions.NAVIGATE_TO_PREV_GROUP) { _ ->
      scrollList(false)
    }

    val escape = ActionManager.getInstance().getAction("EditorEscape")
    DumbAwareAction.create { closePopup() }
      .registerCustomShortcutSet(escape?.shortcutSet ?: CommonShortcuts.ESCAPE, this)

    textField.addFocusListener(object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        onFocusLost(e)
      }
    })
  }

  /**
   * @param down if true, jumps down by approximately one page. If the target element is not loaded, jumps to the last available item;
   *             if false, jumps to the first item in the list
   */
  private fun scrollList(down: Boolean) {
    if (resultList.model.size == 0) return
    if (down) {
      val cellHeight = resultList.getCellBounds(0, 0)?.height ?: return
      val viewportHeight = resultsScrollPane.viewport.height
      val visibleRowCount = viewportHeight / cellHeight

      val shiftSize = maxOf(1, visibleRowCount - 3)
      val targetIndex = resultList.selectedIndex + shiftSize
      val modelSize = resultList.model.size
      val hasMoreRow = modelSize > 0 && resultList.model.getElementAt(modelSize - 1) is SeResultListMoreRow

      val newSelectedIndex = when {
        targetIndex >= modelSize - 1 && hasMoreRow -> maxOf(modelSize - 2, 0)
        targetIndex >= modelSize -> modelSize - 1
        else -> targetIndex
      }

      resultList.selectedIndex = newSelectedIndex
      ScrollingUtil.ensureIndexIsVisible(resultList, newSelectedIndex, 1)
    }
    else {
      resultList.selectedIndex = 0
      ScrollingUtil.ensureIndexIsVisible(resultList, 0, -1)
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

  private fun onFocusLost(e: FocusEvent) {
    if (isWaylandToolkit()) {
      // In Wayland focus is always lost when the window is being moved.
      return
    }
    if (ApplicationManagerEx.isInIntegrationTest()) {
      return
    }

    val oppositeComponent = e.oppositeComponent
    if (!UIUtil.haveCommonOwner(this, oppositeComponent)) {
      closePopup()
    }
  }

  private fun addHistoryExtensionToTextField() {
    textField.addExtension(
      object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean): Icon {
          return if (isExtendedInfoEnabled()) AllIcons.Actions.SearchWithHistory else AllIcons.Actions.Search
        }

        override fun isIconBeforeText(): Boolean {
          return true
        }

        override fun getIconGap(): Int {
          return scale(if (isNewUI()) 6 else 10)
        }

        override fun getActionOnClick(): Runnable? {
          if (!isExtendedInfoEnabled()) return null

          val bounds = (textField.getUI() as TextFieldWithPopupHandlerUI).getExtensionIconBounds(this)
          val point = bounds.location
          point.y += bounds.width + scale(2)
          val relativePoint = RelativePoint(textField, point)
          return Runnable { showHistoryPopup(relativePoint) }
        }
      })
  }

  private fun showHistoryPopup(relativePoint: RelativePoint) {
    val items = vm.getHistoryItems()

    if (items.isEmpty()) return

    JBPopupFactory.getInstance().createPopupChooserBuilder(items)
      .setMovable(false)
      .setRequestFocus(true)
      .setItemChosenCallback { text: String ->
        textField.setText(text)
        textField.selectAll()
      }
      .setRenderer(GroupedItemsListRenderer(
        object : ListItemDescriptorAdapter<String>() {
          override fun getTextFor(value: @NlsContexts.ListItem String?): String? {
            return value
          }
        }
      ))
      .createPopup()
      .show(relativePoint)
  }

  private fun createExtendedInfoComponent(): ExtendedInfoComponent? {
    if (isExtendedInfoEnabled()) {
      val leftText = fun(element: Any): String? {
        val leftText = (element as? SeResultListItemRow)?.item?.presentation?.extendedDescription
        extendedInfoContainer.isVisible = !leftText.isNullOrEmpty()
        return leftText
      }
      return ExtendedInfoComponent(project, ExtendedInfo(leftText) { null })
    }
    return null
  }

  private fun updateExtendedInfoContainer() {
    extendedInfoContainer.removeAll()
    extendedInfoComponent = createExtendedInfoComponent()
    extendedInfoComponent?.let { extendedInfoContainer.add(it.component) }
  }

  private fun closePopup() {
    vm.closePopup()
  }

  private suspend fun updateEmptyStatus() {
    resultList.emptyText.clear()

    if (textField.text.isEmpty()) {
      return
    }

    val emptyResultInfo = vm.currentTab.getEmptyResultInfo(DataManager.getInstance().getDataContext(this@SePopupContentPane))
    emptyResultInfo?.chunks?.forEach { (text, newLine, attrs, listener) ->
      if (newLine) {
        resultList.emptyText.appendLine(text, attrs, listener)
      }
      else {
        resultList.emptyText.appendText(text, attrs, listener)
      }
    }
  }

  private fun updateViewMode() {
    if (textField.text.isEmpty() && resultList.isEmpty) {
      updateViewMode(true)
    }
    else {
      updateViewMode(false)
    }
  }

  private fun updateViewMode(compact: Boolean) {
    extendedInfoContainer.isVisible = !compact && isExtendedInfoEnabled()
    resultsScrollPane.isVisible = !compact

    if (compact == isCompactViewMode) return
    isCompactViewMode = compact

    resizePopupHandler(calcPreferredSize(isCompactViewMode))
  }

  fun getExpandedSize(): Dimension {
    return calcPreferredSize(false)
  }

  override fun getPreferredSize(): Dimension {
    return calcPreferredSize(isCompactViewMode)
  }

  override fun getMinimumSize(): Dimension = getMinimumSize(isCompactViewMode)

  fun getMinimumSize(isCompact: Boolean): Dimension {
    val compactHeight = calcPreferredSize(true).height
    val minimumHeight = if (isCompact) compactHeight else compactHeight + scale(100)
    return Dimension(JBUI.scale(minWidth), minimumHeight)
  }

  private fun calcPreferredSize(compact: Boolean): Dimension {
    val preferredHeight = if (compact) {
      headerPane.preferredSize.height + textField.preferredSize.height
    }
    else {
      popupExtendedSize?.height ?: JBUI.CurrentTheme.BigPopup.maxListHeight()
    }
    return Dimension(popupExtendedSize?.width ?: resultsScrollPane.preferredSize.width, preferredHeight)
  }

  private fun logTabSwitchedEvent(e: AnActionEvent) {
    SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(project,
                                                           SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(vm.currentTab.tabId),
                                                           EventFields.InputEventByAnAction.with(e),
                                                           SearchEverywhereUsageTriggerCollector.IS_SPLIT.with(true))
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.PREDEFINED_TEXT] = textField.text
  }

  /**
   * Custom move up action that moves to the end if the index is 0 and the search is completed
   */
  private inner class MoveUpAction() : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val currentIndex = resultList.selectedIndex
      if (currentIndex == -1) return

      val newIndex = if (currentIndex == 0) {
        if (!isSearchCompleted.load()) return
        // Move to the last item if the search is completed
        resultList.model.size - 1
      }
      else {
        // Move to the previous item
        currentIndex - 1
      }

      resultList.selectedIndex = newIndex
      ScrollingUtil.ensureIndexIsVisible(resultList, newIndex, -1)
    }
  }

  override fun dispose() {}

  companion object {
    const val DEFAULT_FROZEN_VISIBLE_PART: Double = 1.1
    const val DEFAULT_FREEZING_DELAY_MS: Long = 800

    @JvmStatic
    fun isExtendedInfoEnabled(): Boolean {
      return Registry.`is`("search.everywhere.footer.extended.info") || ApplicationManager.getApplication().isInternal()
    }
  }
}
