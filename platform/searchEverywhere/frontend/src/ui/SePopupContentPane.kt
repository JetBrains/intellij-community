// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.actions.searcheverywhere.ExtendedInfo
import com.intellij.ide.actions.searcheverywhere.HintHelper
import com.intellij.ide.actions.searcheverywhere.SEResultsListFactory
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.ide.actions.searcheverywhere.footer.ExtendedInfoComponent
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI
import com.intellij.ide.util.gotoByName.QuickSearchComponent
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CommonShortcuts
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.data.SeDataKeys
import com.intellij.platform.searchEverywhere.frontend.AutoToggleAction
import com.intellij.platform.searchEverywhere.frontend.SeSearchStatePublisher
import com.intellij.platform.searchEverywhere.frontend.SeSelectionListener
import com.intellij.platform.searchEverywhere.frontend.SeSelectionResultClose
import com.intellij.platform.searchEverywhere.frontend.SeSelectionResultText
import com.intellij.platform.searchEverywhere.frontend.SeSelectionState
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereFrontendBundle
import com.intellij.platform.searchEverywhere.frontend.tabs.actions.SeActionItemPresentationRenderer
import com.intellij.platform.searchEverywhere.frontend.tabs.all.SeAllTab
import com.intellij.platform.searchEverywhere.frontend.tabs.files.SeTargetItemPresentationRenderer
import com.intellij.platform.searchEverywhere.frontend.tabs.text.SeTextSearchItemPresentationRenderer
import com.intellij.platform.searchEverywhere.frontend.vm.SeDummyTabVm
import com.intellij.platform.searchEverywhere.frontend.vm.SePopupVm
import com.intellij.platform.searchEverywhere.presentations.SeActionItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTextSearchItemPresentation
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.withPresentation
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI.Companion.isNewUI
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.SearchTextField
import com.intellij.ui.WindowMoveListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.SearchFieldWithExtension
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.gridLayout.GridLayout
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.dsl.gridLayout.VerticalAlign
import com.intellij.ui.dsl.gridLayout.builders.RowsGridBuilder
import com.intellij.ui.popup.PopupUpdateProcessorBase
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.impl.UsagePreviewPanel
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil.isWaylandToolkit
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.KeyStroke
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ListSelectionEvent
import javax.swing.text.Document
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalAtomicApi::class, ExperimentalCoroutinesApi::class)
@Internal
class SePopupContentPane(
  private val project: Project?,
  private val resizePopupHandler: (Dimension) -> Unit,
  private val searchStatePublisher: SeSearchStatePublisher,
  private val coroutineScope: CoroutineScope,
  initialTabs: List<SeDummyTabVm>,
  selectedTabId: String,
  initialSearchText: String?,
  initPopupExtendedSize: Dimension?,
  initialSelectionState: SeSelectionState?,
) : JPanel(), Disposable, UiDataProvider, QuickSearchComponent {
  val preferableFocusedComponent: JComponent get() = textField
  val searchFieldDocument: Document get() = textField.document
  private val tabConfigurationState = MutableStateFlow(SePopupHeaderPane.Configuration.createInitial(initialTabs, selectedTabId))
  private val vmState = MutableStateFlow<SePopupVm?>(null)
  private val contentPane = this

  private val headerPane: SePopupHeaderPane = SePopupHeaderPane(
    project,
    coroutineScope,
    tabConfigurationState,
  ) { updatePopupWidthIfNecessary() }

  val visibleTabsInfo: List<SePopupHeaderPane.Tab>
    get() = tabConfigurationState.value.tabs

  private val minWidth = Registry.intValue("search.everywhere.new.minimum.width", 700)

  private val resultListModel = SeResultListModel(searchStatePublisher) { resultList.selectionModel }
  private val resultList: SeResultJBList<SeResultListRow> = SeResultJBList(resultListModel)
  private var selectionListener = SeSelectionListener(initialSelectionState, resultList, resultListModel)
  private val textField = SeTextField(initialSearchText) { resultList.accessibleContext }
  private val hintHelper = HintHelper(textField)
  private val resultsScrollPane = createListPane(resultList)
  private val usagePreviewPanel = createUsagePreviewPanel()
  private val splitter = createSplitter()

  private val extendedInfoContainer: JComponent = JPanel(BorderLayout())
  private var extendedInfoComponent: ExtendedInfoComponent? = null

  private val isSearchCompleted: AtomicBoolean = AtomicBoolean(false)
  private val adaptedProviderRenderersCache = mutableMapOf<SeProviderId, ListCellRenderer<Any>>()

  var isCompactViewMode: Boolean = true
    private set
  var popupExtendedSize: Dimension? = initPopupExtendedSize

  private val semanticWarning = MutableStateFlow(false)

  private var quickDocPopup: JBPopup? = null

  val currentResultsInList: List<SeItemData> get() =
    resultListModel.elements().iterator().asSequence().mapNotNull { (it as? SeResultListItemRow)?.item }.toList()

  init {
    layout = GridLayout()

    resultList.putClientProperty(RenderingUtil.FOCUSABLE_SIBLING, textField)
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
        is SeResultListItemRow if value.item.presentation is SeAdaptedItemPresentation -> {
          val adaptedPresentation = value.item.presentation as SeAdaptedItemPresentation
          SEResultsListFactory.getNonMoreElementRenderer(null, null, resultList, adaptedPresentation.fetchedItem, index, isSelected) {
            adaptedProviderRenderersCache.computeIfAbsent(value.item.providerId) {
              adaptedPresentation.rendererProvider()
            }
          }
        }
        else -> {
          defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        }
      }
    })

    resultList.setFocusable(false)

    RowsGridBuilder(this)
      .row().cell(headerPane, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)
      .row().cell(wrapSearchField(), horizontalAlign = HorizontalAlign.FILL, resizableColumn = true) //
      .row(resizable = true).cell(splitter, horizontalAlign = HorizontalAlign.FILL, verticalAlign = VerticalAlign.FILL, resizableColumn = true)
      .row().cell(extendedInfoContainer, horizontalAlign = HorizontalAlign.FILL, resizableColumn = true)


    if (textField.text.isNotEmpty()) {
      isCompactViewMode = false
    }

    // hide resultsScrollPane and extendedInfoContainer if isCompactViewMode = true
    updateViewMode()

    addHistoryExtensionToTextField()
    WindowMoveListener(this).installTo(headerPane)

    coroutineScope.launch {
      vmState.filterNotNull().collectLatest { vm ->
        connectTo(vm)
      }
    }
  }

  private fun wrapSearchField(): JComponent {
    if (!Registry.`is`("search.everywhere.round.text.field", false)) {
      return textField
    }

    val wrapper = Wrapper(SearchFieldWithExtension(textField, JBUI.CurrentTheme.Popup.BACKGROUND))
    wrapper.isOpaque = true
    wrapper.background = JBUI.CurrentTheme.Popup.BACKGROUND
    wrapper.border = JBUI.Borders.empty(3, 5)
    return wrapper
  }

  fun setVm(vm: SePopupVm) {
    vmState.value = vm
  }

  private suspend fun connectTo(vm: SePopupVm) = coroutineScope {
    DumbAwareAction.create { vm.getHistoryItem(true).let { textField.text = it; textField.selectAll() } }
      .registerCustomShortcutSet(SearchTextField.SHOW_HISTORY_SHORTCUT, contentPane)
    DumbAwareAction.create { vm.getHistoryItem(false).let { textField.text = it; textField.selectAll() } }
      .registerCustomShortcutSet(SearchTextField.ALT_SHOW_HISTORY_SHORTCUT, contentPane)

    launch {
      vm.tabsModelFlow.map {
        SePopupHeaderPane.Configuration(it.sortedTabVms.map { tabVm -> SePopupHeaderPane.Tab(tabVm) }, it.selectedTabIndexFlow)
      }.collectLatest {
        tabConfigurationState.value = it
      }
    }

    withContext(Dispatchers.UI) {
      textField.configure(vm.searchPattern.value) { newText ->
        vm.setSearchText(newText)
      }
    }

    launch {
      vm.currentTabFlow.flatMapLatest {
        withContext(Dispatchers.EDT) {
          resultListModel.reset()
          semanticWarning.value = resultListModel.isValidAndHasOnlySemantic
        }
        it.searchResults.filterNotNull()
      }.collectLatest { searchContext ->
        val searchId = searchContext.searchId
        val throttledResultEventFlow = searchContext.resultsFlow

        coroutineScope {
          withContext(Dispatchers.EDT) {
            SearchEverywhereUI.associateMatcherToResultsList(resultList, searchContext.searchPattern, searchContext.searchPattern)

            isSearchCompleted.store(false)
            resultListModel.invalidate()
            searchStatePublisher.searchStarted(searchId, textField.text, vm.currentTab.tabId)

            if (vm.searchPattern.value.isNotEmpty()) {
              hintHelper.setSearchInProgress(true)
            }
          }

          launch {
            SeLog.log(SeLog.FROZEN_COUNT) { "Will schedule freeze" }
            delay(DEFAULT_FREEZING_DELAY_MS.milliseconds)
            withContext(Dispatchers.EDT) {
              SeLog.log(SeLog.FROZEN_COUNT) { "Will freeze, because of the scheduled freezing" }
              resultListModel.freezer.enable()
            }
          }

          throttledResultEventFlow.onCompletion {
            withContext(Dispatchers.EDT) {
              SeLog.log(SeLog.THROTTLING) { "Throttled flow completed" }
              isSearchCompleted.store(true)
              resultListModel.removeLoadingItem()
              searchStatePublisher.searchStoppedProducingResults(searchId, resultListModel.size, true)

              if (!resultListModel.isValid || resultListModel.isEmpty) {
                if (!textField.text.isEmpty()) {
                  val currentTab = vm.currentTab
                  if (currentTab.tabId == searchContext.tabId) {

                    if ((currentTab.getSearchEverywhereToggleAction() as? AutoToggleAction)?.autoToggle(true) ?: false) {
                      currentTab.lastNotFoundString = textField.text
                      headerPane.updateActionsAsync()
                      return@withContext
                    }

                  }
                }
              }

              if (!resultListModel.isValid) resultListModel.reset()

              if (resultListModel.isEmpty) {
                hintHelper.setSearchInProgress(false)
                updateEmptyStatus()
                hideQuickDocPopup()
              }

              semanticWarning.value = resultListModel.isValidAndHasOnlySemantic
              updateViewMode()
              autoSelectIndex(searchContext.searchPattern, true)
            }
          }.collect { event ->
            withContext(Dispatchers.EDT) {
              hintHelper.setSearchInProgress(false)
              val wasFrozen = resultListModel.freezer.isEnabled

              resultListModel.addFromThrottledEvent(searchContext, event)
              semanticWarning.value = resultListModel.isValidAndHasOnlySemantic

              // Freeze back if it was frozen before
              if (wasFrozen) {
                SeLog.log(SeLog.FROZEN_COUNT) { "Will freeze, because of it was frozen before" }
                resultListModel.freezer.enable()
              }
              updateFrozenCount()

              updateViewMode()
              autoSelectIndex(searchContext.searchPattern, false)
            }
          }
        }
      }
    }

    launch {
      vm.currentTabFlow.collectLatest { tabVm ->
        val filterEditor = tabVm.filterEditor.getValue()
        withContext(Dispatchers.EDT) {
          if (!isActive) return@withContext

          hintHelper.removeRightExtensions()

          if (filterEditor == null) {
            headerPane.setFilterActions(emptyList(), vm.ShowInFindToolWindowAction())
          }
          else {
            headerPane.setFilterActions(filterEditor.getHeaderActions(), vm.ShowInFindToolWindowAction())
            val rightActions = filterEditor.getSearchFieldActions()
            if (rightActions.isNotEmpty()) {
              hintHelper.setRightExtensions(rightActions)
            }
          }
        }
        withContext(Dispatchers.EDT) {
          updateExtendedInfoContainer()
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

    launch {
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

    launch {
      vm.currentTabFlow.flatMapLatest {
        it.resultsHitBackPressureFlow
      }.collect { (searchId, _) ->
        withContext(Dispatchers.EDT) {
          searchStatePublisher.searchStoppedProducingResults(searchId, resultListModel.size, false)
        }
      }
    }

    launch {
      combine(vm.searchFieldHint, semanticWarning) { searchFieldHint, semanticWarning ->
        searchFieldHint to semanticWarning
      }.collect { (searchFieldHint, semanticWarning) ->
        withContext(Dispatchers.EDT) {
          if (searchFieldHint != null) {
            val (text, tooltip, isWarning) = searchFieldHint
            if (isWarning) {
              hintHelper.setLoadingText(text, tooltip)
            }
            else {
              hintHelper.setHint(text)
            }
          }
          else if (semanticWarning) {
            val noExactMatchesText = SearchEverywhereFrontendBundle.bundle.getMessage("search.everywhere.no.exact.matches")
            hintHelper.setHint(noExactMatchesText)
          }
          else {
            hintHelper.setHint(null)
          }
        }
      }
    }

    val selectedItemDataStateFlow = MutableStateFlow<SeItemData?>(null)

    resultList.addListSelectionListener {
      selectedItemDataStateFlow.value = (resultList.selectedValue as? SeResultListItemRow)?.item
    }

    val selectedItemDataFlow = selectedItemDataStateFlow.distinctUntilChanged { old, new ->
      if (new == null && old == null) true
      else old?.presentation?.contentEquals(new?.presentation) == true
    }

    vm.coroutineScope.launch {
      vm.previewConfigurationFlow.collectLatest { configuration ->
        val isVisible = configuration?.fetchPreview != null

        if (!isVisible) {
          withContext(Dispatchers.EDT) {
            usagePreviewPanel?.isVisible = false
          }
          return@collectLatest
        }

        selectedItemDataFlow.collectLatest { itemData ->
          withContext(Dispatchers.EDT) {
            if (itemData != null) {
              val usageInfos = configuration.fetchPreview(itemData)
              usagePreviewPanel?.isVisible = true
              usagePreviewPanel?.updateLayout(configuration.project, usageInfos)
            }
            else {
              usagePreviewPanel?.isVisible = false
            }
          }
        }
      }
    }

    vm.coroutineScope.launch {
      selectedItemDataFlow.collectLatest { itemData ->
        withContext(Dispatchers.EDT) {
          updateQuickDocPopup(itemData)
        }
      }
    }
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

        if (originalStroke.modifiers and modifiers != 0) continue

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
        coroutineScope.launch(Dispatchers.EDT) {
          elementsSelected(indices, modifiers)
        }
      }.registerCustomShortcutSet(newShortcutSet, this, this)
    }
  }

  @Internal
  fun selectFirstItem() {
    coroutineScope.launch(Dispatchers.EDT) {
      elementsSelected(intArrayOf(0), 0)
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
        originalIndex - nonItemDataCount to row.item
      }
      else {
        nonItemDataCount++
        null
      }
    }

    val selectedItems = vmState.value?.itemsSelected(itemDataList, nonItemDataCount == 0, modifiers)
    if (selectedItems?.any { it is SeSelectionResultClose } == true) {
      closePopup()
    }
    else {
      (selectedItems?.filterIsInstance<SeSelectionResultText>()?.firstOrNull())?.let { textField.text = it.searchText + " " }

      resultList.repaint()
      refreshPresentations()
    }
  }

  private suspend fun refreshPresentations() {
    val currentTab = vmState.value?.currentTab ?: return

    val listSize = resultListModel.size
    val firstIndex = resultList.firstVisibleIndex.takeIf { it in 0..<listSize } ?: return
    val lastIndex = resultList.lastVisibleIndex.takeIf { it in 0..<listSize && it >= firstIndex } ?: return

    val visibleRows = (firstIndex..lastIndex).mapNotNull {
      resultListModel.get(it) as? SeResultListItemRow
    }

    coroutineScope {
      visibleRows.forEach { itemRow ->
        val item = itemRow.item

        launch {
          val newPresentation = currentTab.getUpdatedPresentation(item)
          if (newPresentation != null) {
            val newItemRow = SeResultListItemRow(item.withPresentation(newPresentation))

            withContext(Dispatchers.EDT) {
              val index = resultListModel.indexOf(itemRow).takeIf { it != -1 } ?: return@withContext
              resultListModel.set(index, newItemRow)
            }
          }
        }
      }
    }
  }

  private fun installScrollingActions() {
    val moveUpAction = MoveUpAction()
    moveUpAction.registerCustomShortcutSet(
      CommonShortcuts.getMoveUp(),
      textField
    )

    ScrollingUtil.installMoveDownAction(resultList, textField)

    resultList.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION

    resultList.addListSelectionListener { _: ListSelectionEvent ->
      val selectedIndices = resultList.selectedIndices
      if (selectedIndices.size > 1) {
        val multiSelection = selectedIndices.all { i ->
          val element = resultListModel.get(i)
          element is SeResultListItemRow && element.item.presentation.isMultiSelectionSupported
        }
        if (!multiSelection) {
          val leadSelectionIndex = resultList.leadSelectionIndex
          resultList.setSelectedIndex(leadSelectionIndex)
        }
      }

      val firstSelectedIndex = resultList.selectedIndex
      if (firstSelectedIndex != -1) {
        extendedInfoComponent?.updateElement(resultList.selectedValue, this@SePopupContentPane)
      }
    }

    resultList.addListSelectionListener { _: ListSelectionEvent ->
      if (!resultList.isAutoSelectionChange) {
        selectionListener.saveSelectionState(textField.text)
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
        }
      }
    }

    resultList.addMouseMotionListener(listMouseListener)
    resultList.addMouseListener(listMouseListener)

    ScrollingUtil.redirectExpandSelection(resultList, textField)

    val nextTabAction: (AnActionEvent) -> Unit = { e ->
      vmState.value?.let { vm ->
        vm.selectNextTab()
        logTabSwitchedEvent(e)
      }
    }
    val prevTabAction: (AnActionEvent) -> Unit = { e ->
      vmState.value?.let { vm ->
        vm.selectPreviousTab()
        logTabSwitchedEvent(e)
      }
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
      val visibleRowCount = getMaxVisibleRowCount()

      val shiftSize = maxOf(1, visibleRowCount - 4)
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
    val isPreviewDisabledOrDoubleClick = !SearchEverywhereUI.isPreviewActive() || vmState.value?.currentTab?.isPreviewEnabled?.getValueOrNull() == false || e.clickCount == 2

    if (e.button == MouseEvent.BUTTON1 && !multiSelectMode && isPreviewDisabledOrDoubleClick) {
      e.consume()
      val i: Int = resultList.locationToIndex(e.point)
      if (i > -1) {
        resultList.setSelectedIndex(i)
        val modifiers = e.modifiersEx
        coroutineScope.launch {
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
          return AllIcons.Actions.SearchWithHistory
        }

        override fun isIconBeforeText(): Boolean {
          return true
        }

        override fun getIconGap(): Int {
          return scale(if (isNewUI()) 6 else 10)
        }

        override fun getActionOnClick(): Runnable {
          val bounds = (textField.getUI() as TextFieldWithPopupHandlerUI).getExtensionIconBounds(this)
          val point = bounds.location
          point.y += bounds.width + scale(2)
          val relativePoint = RelativePoint(textField, point)
          return Runnable { showHistoryPopup(relativePoint) }
        }
      })
  }

  private fun showHistoryPopup(relativePoint: RelativePoint) {
    val vm = vmState.value ?: return
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

  private suspend fun createExtendedInfoComponent(): ExtendedInfoComponent? {
    if (vmState.value?.isExtendedInfoEnabled() == true) {
      val leftText = fun(element: Any): String? {
        val leftText = (element as? SeResultListItemRow)?.item?.presentation?.extendedInfo?.text
        return leftText
      }

      val rightAction = fun(element: Any?): AnAction? {
        val extendedInfo = (element as? SeResultListItemRow)?.item?.presentation?.extendedInfo
        val actionText = extendedInfo?.actionText
        val actionDescription = extendedInfo?.actionDescription
        val item = (element as? SeResultListItemRow)?.item ?: return null

        return object : DumbAwareAction({ actionText }, { actionDescription }) {
          override fun actionPerformed(e: AnActionEvent) {
            coroutineScope.launch {
              if (vmState.value?.currentTab?.performExtendedAction(item) == true) {
                withContext(Dispatchers.EDT) {
                  closePopup()
                }
              }
            }
          }
        }.apply {
          if (extendedInfo?.keyCode != null && extendedInfo.modifiers != null) {
            val shortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke(extendedInfo.keyCode!!, extendedInfo.modifiers!!))
            registerCustomShortcutSet(shortcutSet, resultList, this@SePopupContentPane)
          }
        }
      }

      return ExtendedInfoComponent(project, ExtendedInfo(leftText, rightAction))
    }
    return null
  }

  private suspend fun updateExtendedInfoContainer() {
    extendedInfoContainer.removeAll()
    extendedInfoComponent = createExtendedInfoComponent()
    extendedInfoComponent?.let { extendedInfoContainer.add(it.component) }
  }

  private fun closePopup() {
    coroutineScope.launch(Dispatchers.EDT) {
      hideQuickDocPopup()
    }
    vmState.value?.closePopup()
  }

  private suspend fun updateEmptyStatus() {
    resultList.emptyText.clear()

    if (textField.text.isEmpty()) {
      return
    }

    val vm = vmState.value ?: return
    val emptyResultInfo = vm.currentTab.getEmptyResultInfo(DataManager.getInstance().getDataContext(this@SePopupContentPane))
    emptyResultInfo?.chunks?.forEach { chunk ->
      if (chunk.onNewLine) {
        resultList.emptyText.appendLine(chunk.text, chunk.attrs, chunk.listener)
      }
      else {
        resultList.emptyText.appendText(chunk.text, chunk.attrs, chunk.listener)
      }
    }
  }

  private fun updateViewMode() {
    if (textField.text.isEmpty() && resultList.isEmpty && !(ScreenReader.isActive() && SystemInfoRt.isMac)) {
      updateViewMode(true)
    }
    else {
      updateViewMode(false)
    }
  }

  private fun updateViewMode(compact: Boolean) {
    extendedInfoContainer.isVisible = !compact

    if (compact == isCompactViewMode) return
    isCompactViewMode = compact

    updatePopupSize()
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

  private fun updatePopupWidthIfNecessary() {
    if (!isShowing || popupExtendedSize != null) return
    if (headerPane.width < headerPane.preferredSize.width) updatePopupSize()
  }

  private fun updatePopupSize() {
    if (!isShowing) return
    resizePopupHandler(calcPreferredSize(isCompactViewMode, true))
  }

  private fun calcPreferredSize(compact: Boolean, avoidWidthDecreasing: Boolean = false): Dimension {
    val preferredHeight = if (compact) {
      val extraHeight = if (Registry.`is`("search.everywhere.round.text.field", false)) JBUI.scale(15) else 0
      headerPane.preferredSize.height + textField.preferredSize.height + extraHeight
    }
    else {
      getPopupExtendedHeight()
    }

    val preferredWidth = popupExtendedSize?.width ?: maxOf(resultsScrollPane.preferredSize.width,
                                                           headerPane.preferredSize.width,
                                                           if (avoidWidthDecreasing) headerPane.width else 0)
    return Dimension(preferredWidth, preferredHeight)
  }

  private fun getPopupExtendedHeight(): Int {
    return popupExtendedSize?.height ?: JBUI.CurrentTheme.BigPopup.maxListHeight()
  }

  private fun logTabSwitchedEvent(e: AnActionEvent) {
    val vm = vmState.value ?: return
    SearchEverywhereUsageTriggerCollector.TAB_SWITCHED.log(project,
                                                           SearchEverywhereUsageTriggerCollector.CONTRIBUTOR_ID_FIELD.with(vm.currentTab.tabId),
                                                           EventFields.InputEventByAnAction.with(e),
                                                           SearchEverywhereUsageTriggerCollector.IS_SPLIT.with(true))
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.PREDEFINED_TEXT] = textField.text
    sink[CommonDataKeys.PROJECT] = project

    vmState.value?.let { vm ->
      sink[SeDataKeys.SPLIT_SE_SESSION] = vm.session
      sink[SeDataKeys.SPLIT_SE_IS_ALL_TAB] = vm.currentTab.tabId == SeAllTab.ID
    }

    val selectedItems = resultList.selectedIndices.toList().mapNotNull {
      if (it < 0 || resultList.model.size <= it) return@mapNotNull null
      val row = resultListModel.get(it)
      (row as? SeResultListItemRow)?.item
    }

    sink[SeDataKeys.SPLIT_SE_SELECTED_ITEMS] = selectedItems
  }

  /**
   * Custom move up action that moves to the end if the index is 0 and the search is completed
   */
  private inner class MoveUpAction : DumbAwareAction() {
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

  private fun createUsagePreviewPanel(): UsagePreviewPanel? {
    if (project == null) return null

    val usageViewPresentation = UsageViewPresentation()
    val usagePreviewPanel = object : UsagePreviewPanel(project, usageViewPresentation, true) {
      override fun getPreferredSize(): Dimension {
        return Dimension(headerPane.width, this.height.coerceAtLeast(lineHeight * 10))
      }

      override fun onEditorCreated(editor: Editor) {
        if (editor is EditorEx) {
          editor.setRendererMode(true)
        }

        editor.getContentComponent().addFocusListener(object : FocusAdapter() {
          override fun focusLost(e: FocusEvent) {
            onFocusLost(e)
          }
        })

        // todo (rider statistics): myPreviewTopicPublisher.onPreviewEditorCreated(this@SePopupContentPane, editor)
      }
    }

    usagePreviewPanel.background = JBUI.CurrentTheme.Popup.BACKGROUND

    return usagePreviewPanel
  }

  private fun createSplitter(): OnePixelSplitter {
    val splitter = OnePixelSplitter(true, .33f)
    splitter.splitterProportionKey = SearchEverywhereUI.SPLITTER_SERVICE_KEY
    splitter.divider.setBackground(JBUI.CurrentTheme.Separator.color())
    splitter.setFirstComponent(resultsScrollPane)
    splitter.setSecondComponent(usagePreviewPanel)
    return splitter
  }

  /**
   * Calculates the number of rows that can be visible in the scroll pane, including partially visible rows.
   *
   * @return the total count of visible rows (both fully and partially visible), or -1 if cell height cannot be determined
   */
  private fun getMaxVisibleRowCount(): Int {
    val cellHeight = resultList.getCellBounds(0, 0)?.height ?: -1
    val scrollPaneHeight = getPopupExtendedHeight() - headerPane.height - textField.height - (extendedInfoComponent?.component?.height ?: 0)
    return ceil(scrollPaneHeight.toDouble() / cellHeight).toInt()
  }

  private fun autoSelectIndex(searchPattern: String, isEndEvent: Boolean) {
    val indexToSelect = selectionListener.getIndexToSelect(getMaxVisibleRowCount(), searchPattern, textField.isInitialSearchPattern, isEndEvent)
    if (indexToSelect != -1 && indexToSelect < resultListModel.size() && indexToSelect != resultList.selectedIndex) {
      resultList.autoSelectIndex(indexToSelect)
      ScrollingUtil.ensureIndexIsVisible(resultList, resultList.selectedIndex, 1)
    }
  }

  fun getSelectionState() : SeSelectionState? {
    return selectionListener.getSelectionState()
  }

  @TestOnly
  fun getResultListModel(): SeResultListModel {
    return resultListModel
  }

  override fun registerHint(h: JBPopup) {
    quickDocPopup?.takeIf { it.isVisible && it != h }?.cancel()
    quickDocPopup = h
  }

  override fun unregisterHint() {
    quickDocPopup = null
  }

  /**
   * IJPL-188794 Quick doc popup and Quick def popup are not updated in remote development for several reasons:
   * - Most elements cannot be fetched on the frontend
   * - Showing the documentation popup triggers PopupUpdateProcessor.beforeShown on the backend,
   *   which registers the hint based on the focused component. As a result,
   *   SePopupContentPane.registerHint is never called
   */
  private fun updateQuickDocPopup(itemData: SeItemData?) {
    val quickDocPopup = quickDocPopup ?: return
    if (!quickDocPopup.isVisible()) return
    val rawObject = itemData?.fetchItemIfExists()?.rawObject ?: hideQuickDocPopup()

    val updateProcessor = quickDocPopup.getUserData(PopupUpdateProcessorBase::class.java)
    updateProcessor?.updatePopup(rawObject)
  }

  private fun hideQuickDocPopup() {
    quickDocPopup?.takeIf { it.isVisible }?.cancel()
  }

  override fun dispose() {
    usagePreviewPanel?.let { Disposer.dispose(it) }
  }

  companion object {
    const val DEFAULT_FROZEN_VISIBLE_PART: Double = 1.1
    const val DEFAULT_FREEZING_DELAY_MS: Long = 800
  }
}
