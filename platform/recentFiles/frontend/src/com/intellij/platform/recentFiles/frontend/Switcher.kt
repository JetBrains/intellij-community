// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.ui.JBListWithOpenInRightSplit
import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.gotoByName.QuickSearchComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.getOpenMode
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.LightEditActionFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.project.projectId
import com.intellij.platform.recentFiles.frontend.SwitcherLogger.NAVIGATED
import com.intellij.platform.recentFiles.frontend.SwitcherLogger.NAVIGATED_INDEXES
import com.intellij.platform.recentFiles.frontend.SwitcherLogger.NAVIGATED_ORIGINAL_INDEXES
import com.intellij.platform.recentFiles.frontend.SwitcherLogger.SHOWN_TIME_ACTIVITY
import com.intellij.platform.recentFiles.frontend.SwitcherSpeedSearch.Companion.installOn
import com.intellij.platform.recentFiles.frontend.model.FrontendRecentFilesModel
import com.intellij.platform.recentFiles.shared.FileChangeKind
import com.intellij.platform.recentFiles.shared.FileSwitcherApi
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.RecentFilesBackendRequest
import com.intellij.platform.recentFiles.shared.RecentFilesCoroutineScopeProvider
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.hover.ListHoverListener
import com.intellij.ui.popup.PopupUpdateProcessorBase
import com.intellij.ui.render.RenderingUtil
import com.intellij.ui.speedSearch.FilteringListModel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.SwingTextTrimmer
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.ui.components.BorderLayoutPanel
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.await
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private const val ACTION_PLACE = "Switcher"

object Switcher : BaseSwitcherAction(null), ActionRemoteBehaviorSpecification.Frontend {
  @ApiStatus.Internal
  val SWITCHER_KEY: Key<SwitcherPanel> = Key.create("SWITCHER_KEY")

  @ApiStatus.Internal
  class SwitcherPanel(
    val project: Project,
    val title: @Nls String,
    launchParameters: SwitcherLaunchEventParameters,
    onlyEditedFiles: Boolean?,
    private val frontendModel: FrontendRecentFilesModel,
    private val remoteApi: FileSwitcherApi,
  ) : BorderLayoutPanel(), UiDataProvider, QuickSearchComponent, Disposable {
    val popup: JBPopup?
    private val activity = SHOWN_TIME_ACTIVITY.started(project)
    private var navigationData: SwitcherLogger.NavigationData? = null
    internal val toolWindows: JBList<SwitcherListItem>
    internal val files: JBList<SwitcherVirtualFile>
    private val listModel: CollectionListModel<SwitcherVirtualFile>
    val cbShowOnlyEditedFiles: JCheckBox?
    private val pathLabel: JLabel = HintUtil.createAdComponent(
      " ",
      if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Advertiser.border()
      else JBUI.Borders.compound(
        JBUI.Borders.customLineTop(JBUI.CurrentTheme.Advertiser.borderColor()),
        JBUI.CurrentTheme.Advertiser.border()
      ),
      SwingConstants.LEFT
    )

    // false - Switcher, true - Recent files / Recently changed files
    val recent: Boolean = onlyEditedFiles != null

    // false - auto closeable on modifier key release, true - default popup
    val pinned: Boolean

    private val mnemonicsRegistry: SwitcherMnemonicsRegistry = SwitcherMnemonicsRegistry(launchParameters)
    private val onKeyRelease: SwitcherKeyReleaseListener
    private val speedSearch: SwitcherSpeedSearch?
    private var hint: JBPopup? = null

    private val uiUpdateScope: CoroutineScope
    private val modelUpdateScope: CoroutineScope

    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.PROJECT] = project
      sink[PlatformCoreDataKeys.SELECTED_ITEM] =
        if (files.isSelectionEmpty) null else (files.selectedValuesList.singleOrNull() as? SwitcherVirtualFile)?.virtualFile
      sink[PlatformDataKeys.SPEED_SEARCH_TEXT] =
        if (speedSearch?.isPopupActive == true) speedSearch.enteredPrefix else null
      sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] =
        files.selectedValuesList.filterIsInstance<SwitcherVirtualFile>()
          .mapNotNull { it.virtualFile }
          .takeIf { it.isNotEmpty() }
          ?.toTypedArray()
    }

    private fun setupBottomPanel(): JComponent {
      return RecentFilesAdvertisementProvider.EP_NAME.extensionList.firstNotNullOfOrNull { it.getBanner(project) }?.let { banner ->
        JPanel(BorderLayout()).apply {
          add(pathLabel, BorderLayout.NORTH)
          add(banner, BorderLayout.SOUTH)
        }
      } ?: pathLabel
    }

    init {
      val serviceScope = RecentFilesCoroutineScopeProvider.getInstance(project).coroutineScope
      uiUpdateScope = serviceScope.childScope("Switcher UI updates")
      modelUpdateScope = serviceScope.childScope("Switcher backend requests")

      onKeyRelease = SwitcherKeyReleaseListener(if (recent) null else launchParameters) { e ->
        ActionUtil.performInputEventHandlerWithCallbacks(ActionUiKind.POPUP, ACTION_PLACE, e) {
          navigate(e)
        }
      }
      pinned = !launchParameters.isEnabled
      val onlyEdited = true == onlyEditedFiles
      speedSearch = if (recent && Registry.`is`("ide.recent.files.speed.search")) installOn(this) else null
      cbShowOnlyEditedFiles = if (!recent) null else JCheckBox(IdeBundle.message("recent.files.checkbox.label"))

      val renderer = SwitcherListRenderer(this)

      // register custom actions as soon as possible to block overridden actions
      if (pinned) {
        registerAction(ActionUtil.getShortcutSet("PopupMenu-return")) { navigate(it) }
        registerAction(ActionUtil.getShortcutSet(IdeActions.ACTION_EDITOR_ESCAPE)) { hideSpeedSearchOrPopup() }
        registerAction(ActionUtil.getShortcutSet("DeleteRecentFiles")) { closeTabOrToolWindow() }
        registerAction(ActionUtil.getShortcutSet(IdeActions.ACTION_OPEN_IN_NEW_WINDOW)) { navigate(it) }
        registerAction(ActionUtil.getShortcutSet(IdeActions.ACTION_OPEN_IN_RIGHT_SPLIT)) { navigate(it) }
      }
      else {
        registerAction("ENTER") { navigate(it) }
        registerAction("ESCAPE") { hideSpeedSearchOrPopup() }
        registerAction("DELETE", "BACK_SPACE") { closeTabOrToolWindow() }
        registerSwingAction(ListActions.Up.ID, "KP_UP", "UP")
        registerSwingAction(ListActions.Down.ID, "KP_DOWN", "DOWN")
        registerSwingAction(ListActions.Left.ID, "KP_LEFT", "LEFT")
        registerSwingAction(ListActions.Right.ID, "KP_RIGHT", "RIGHT")
        registerSwingAction(ListActions.PageUp.ID, "PAGE_UP")
        registerSwingAction(ListActions.PageDown.ID, "PAGE_DOWN")
      }

      border = JBUI.Borders.empty()
      pathLabel.putClientProperty(SwingTextTrimmer.KEY, SwingTextTrimmer.THREE_DOTS_AT_LEFT)
      val header = JPanel(HorizontalLayout(5))
      val titleLabel = RelativeFont.BOLD.install(JLabel(title))
      header.add(HorizontalLayout.LEFT, titleLabel)
      if (ExperimentalUI.isNewUI()) {
        background = JBUI.CurrentTheme.Popup.BACKGROUND
        titleLabel.border = PopupUtil.getComplexPopupVerticalHeaderBorder()
        header.background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
        header.border = JBUI.Borders.compound(JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
                                              PopupUtil.getComplexPopupHorizontalHeaderBorder())
      }
      else {
        background = JBColor.background()
        header.background = JBUI.CurrentTheme.Popup.headerBackground(false)
        header.border = JBUI.Borders.empty(4, 8)
      }
      if (cbShowOnlyEditedFiles != null) {
        cbShowOnlyEditedFiles.isOpaque = false
        if (!ScreenReader.isActive()) {
          cbShowOnlyEditedFiles.isFocusable = false
        }
        cbShowOnlyEditedFiles.isSelected = onlyEdited
        cbShowOnlyEditedFiles.addItemListener(ItemListener(::updateFilesByCheckBox))
        header.add(HorizontalLayout.RIGHT, cbShowOnlyEditedFiles)
        WindowMoveListener(header).installTo(header)
        val shortcuts = KeymapUtil.getActiveKeymapShortcuts("SwitcherRecentEditedChangedToggleCheckBox")
        if (shortcuts.shortcuts.isNotEmpty()) {
          val label = JLabel(KeymapUtil.getShortcutsText(shortcuts.shortcuts))
          label.foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
          header.add(HorizontalLayout.RIGHT, label)
        }
      }


      // setup items in the quick access list on the left
      val windows = mutableListOf<SwitcherListItem>()

      val clientToolWindowModels = collectToolWindows(onlyEdited, pinned, speedSearch != null, mnemonicsRegistry, project)
      windows.addAll(clientToolWindowModels)

      if (speedSearch == null || Registry.`is`("ide.recent.files.tool.window.mnemonics")) {
        for (window in clientToolWindowModels) {
          registerToolWindowAction(window)
        }
      }
      if (pinned && !windows.isEmpty()) {
        windows.addAll(listOf(SwitcherRecentLocations(this)))
      }

      val twModel = CollectionListModel(windows.toMutableList(), true)
      toolWindows = JBList(speedSearch?.wrap(twModel) ?: twModel)
      toolWindows.visibleRowCount = toolWindows.itemsCount
      toolWindows.border = JBUI.Borders.empty(5, 0)
      toolWindows.selectionMode = if (pinned) ListSelectionModel.MULTIPLE_INTERVAL_SELECTION else ListSelectionModel.SINGLE_SELECTION
      toolWindows.accessibleContext.accessibleName = IdeBundle.message("recent.files.accessible.tool.window.list")
      toolWindows.setEmptyText(IdeBundle.message("recent.files.tool.window.list.empty.text"))
      toolWindows.setCellRenderer(renderer)
      toolWindows.putClientProperty(RenderingUtil.ALWAYS_PAINT_SELECTION_AS_FOCUSED, true)
      toolWindows.addKeyListener(onKeyRelease)
      PopupUtil.applyNewUIBackground(toolWindows)
      ScrollingUtil.installActions(toolWindows)
      ListHoverListener.DEFAULT.addTo(toolWindows)

      val clickListener: ClickListener = object : ClickListener() {
        override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
          if (pinned && (e.isControlDown || e.isMetaDown || e.isShiftDown)) return false
          val source = e.source
          if (source is JList<*>) {
            if (source.selectedIndex == -1 && source.anchorSelectionIndex != -1) {
              source.selectedIndex = source.anchorSelectionIndex
            }
            if (source.selectedIndex != -1) {
              ActionUtil.performInputEventHandlerWithCallbacks(ActionUiKind.POPUP, ACTION_PLACE, e) {
                navigate(e)
              }
            }
          }
          return true
        }
      }
      clickListener.installOn(toolWindows)

      // setup files
      val initialData = when {
        !pinned -> frontendModel.getRecentFiles(RecentFileKind.RECENTLY_OPENED_UNPINNED)
        onlyEdited -> frontendModel.getRecentFiles(RecentFileKind.RECENTLY_EDITED)
        else -> frontendModel.getRecentFiles(RecentFileKind.RECENTLY_OPENED)
      }
      listModel = CollectionListModel(initialData)
      val maybeSearchableModel = speedSearch?.wrap(listModel) ?: listModel
      maybeSearchableModel.addListDataListener(object : ListDataListener {
        override fun intervalAdded(e: ListDataEvent?) {
          if (e == null) return
          cancelScheduledUiUpdate()
          LOG.debug("Switcher add interval: $e")
          // select the first item, if it's the only one
          if (files.selectionModel.isSelectionEmpty && maybeSearchableModel.size == 1) {
            LOG.debug("Switcher add first item: $e")
            files.requestFocusInWindow()
            files.selectionModel.setSelectionInterval(0, 0)
            return
          }

          // otherwise, select the first file not opened in the focused editor after it is received
          if (files.selectionModel.selectedIndices.isEmpty() && maybeSearchableModel.size > 1) {
            LOG.debug("Switcher add non-first item: $e")
            val firstModelEntry = maybeSearchableModel.getElementAt(0)
            if (firstModelEntry.virtualFile == FileEditorManager.getInstance(project).selectedEditor?.file) {
              LOG.debug("Switcher added item == editor, selecting 2nd item: $e")
              files.selectionModel.setSelectionInterval(1, 1)
            }
            else {
              LOG.debug("Switcher added item != editor, selecting 1st item: $e")
              files.selectionModel.setSelectionInterval(0, 0)
            }
          }
          updatePathLabel()
        }

        override fun intervalRemoved(e: ListDataEvent?) {
          if (maybeSearchableModel.size == 0) {
            LOG.debug("Switcher removed item, empty model: $e")
            scheduleUiUpdate {
              toolWindows.requestFocusInWindow()
              ScrollingUtil.ensureSelectionExists(toolWindows)
            }
          }
          else if (e != null) {
            val lastSelectedIndex = min(e.index0, e.index1)
            val newSelectedIndex = lastSelectedIndex.coerceIn(0, maybeSearchableModel.size - 1)
            files.selectionModel.setSelectionInterval(newSelectedIndex, newSelectedIndex)
          }
        }

        override fun contentsChanged(e: ListDataEvent?) {}
      })

      files = JBListWithOpenInRightSplit.createListWithOpenInRightSplitter<SwitcherVirtualFile>(maybeSearchableModel, null)
      files.visibleRowCount = files.itemsCount

      val filesSelectionListener = object : ListSelectionListener {
        override fun valueChanged(e: ListSelectionEvent) {
          LOG.debug("Switcher value changed: $e")

          if (e.valueIsAdjusting) {
            return
          }

          updatePathLabel()
          val hint = hint
          val popupUpdater = if (hint == null || !hint.isVisible) null else hint.getUserData(PopupUpdateProcessorBase::class.java)
          popupUpdater?.updatePopup(CommonDataKeys.PSI_ELEMENT.getData(DataManager.getInstance().getDataContext(this@SwitcherPanel)))
        }
      }
      toolWindows.selectionModel.addListSelectionListener(filesSelectionListener)
      files.selectionModel.addListSelectionListener(filesSelectionListener)

      if (files.model.size > 0) {
        val fileFromSelectedEditor = FileEditorManager.getInstance(project).selectedEditor?.file
        val firstFileInList = files.model.getElementAt(0).virtualFile
        if (firstFileInList != null && firstFileInList == fileFromSelectedEditor) {
          files.setSelectedIndex(1)
        }
        else {
          files.setSelectedIndex(0)
        }
      }


      files.selectionMode = if (pinned) ListSelectionModel.MULTIPLE_INTERVAL_SELECTION else ListSelectionModel.SINGLE_SELECTION
      files.accessibleContext.accessibleName = IdeBundle.message("recent.files.accessible.file.list")
      files.setEmptyText(IdeBundle.message("recent.files.file.list.empty.text"))
      files.setCellRenderer(renderer)
      files.border = JBUI.Borders.empty(5, 0)
      files.addKeyListener(onKeyRelease)
      PopupUtil.applyNewUIBackground(files)
      ScrollingUtil.installActions(files)
      ListHoverListener.DEFAULT.addTo(files)
      clickListener.installOn(files)
      addToTop(header)
      addToBottom(setupBottomPanel())
      addToCenter(SwitcherScrollPane(files, true))
      if (!windows.isEmpty()) {
        addToLeft(SwitcherScrollPane(toolWindows, false))
      }
      if (speedSearch != null) {
        // copy a speed search listener from the panel to the lists
        val listener = keyListeners.lastOrNull()
        files.addKeyListener(listener)
        toolWindows.addKeyListener(listener)
      }
      popup = JBPopupFactory.getInstance().createComponentPopupBuilder(this, files)
        .setResizable(pinned)
        .setNormalWindowLevel(pinned && StartupUiUtil.isWaylandToolkit()) // On Wayland, only "normal" windows can be moved smoothly at the moment
        .setModalContext(false)
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnWindowDeactivation(true)
        .setCancelOnOtherWindowOpen(true)
        .setMovable(pinned)
        .setDimensionServiceKey(if (pinned) project else null, if (pinned) "SwitcherDM" else null, false)
        .setCancelKeyEnabled(false)
        .createPopup()
      Disposer.register(popup, this)
      popup.setMinimumSize(JBUI.DialogSizes.medium())
      isFocusCycleRoot = true
      if (ScreenReader.isActive()) {
        val list = mutableListOf<Component>(files, toolWindows)
        if (cbShowOnlyEditedFiles != null) {
          list.add(cbShowOnlyEditedFiles)
        }
        focusTraversalPolicy = ListFocusTraversalPolicy(list)
      }
      else {
        focusTraversalPolicy = LayoutFocusTraversalPolicy()
      }
      SwitcherListFocusAction(files, toolWindows, ListActions.Left.ID)
      SwitcherListFocusAction(toolWindows, files, ListActions.Right.ID)
      IdeEventQueue.getInstance().popupManager.closeAllPopups(false)
      val old = project.getUserData(SWITCHER_KEY)
      old?.cancel()
      project.putUserData(SWITCHER_KEY, this)
      popup.showCenteredInCurrentWindow(project)

      if (Registry.`is`("highlighting.passes.cache")) {
        scheduleBackendRecentFilesUpdate(RecentFilesBackendRequest.ScheduleRehighlighting(project.projectId()))
      }
    }

    override fun dispose() {
      project.putUserData(SWITCHER_KEY, null)
      activity.finished {
        buildList {
          NAVIGATED.with(navigationData != null && navigationData!!.navigationIndexes.isNotEmpty())
          if (navigationData != null) {
            NAVIGATED_ORIGINAL_INDEXES.with(navigationData!!.navigationOriginalIndexes)
            NAVIGATED_INDEXES.with(navigationData!!.navigationIndexes)
          }
        }
      }
      uiUpdateScope.cancel(CancellationException("Switcher is disposed"))
      modelUpdateScope.cancel(CancellationException("Switcher is disposed"))
    }

    val isOnlyEditedFilesShown: Boolean
      get() = cbShowOnlyEditedFiles != null && cbShowOnlyEditedFiles.isSelected
    val isSpeedSearchPopupActive: Boolean
      get() = speedSearch != null && speedSearch.isPopupActive

    override fun registerHint(h: JBPopup) {
      if (hint != null && hint!!.isVisible && hint !== h) {
        hint!!.cancel()
      }
      hint = h
    }

    override fun unregisterHint() {
      hint = null
    }

    private fun updatePathLabel() {
      val values = selectedList?.selectedValuesList
      val statusText = values?.singleOrNull()?.statusText
      pathLabel.text = if (statusText.isNullOrEmpty()) " " else statusText
    }

    private fun closeTabOrToolWindow() {
      if (speedSearch != null && speedSearch.isPopupActive) {
        speedSearch.updateEnteredPrefix()
        return
      }
      val selectedList: JList<out SwitcherListItem>? = selectedList
      val selected = selectedList!!.selectedIndices
      Arrays.sort(selected)
      var selectedIndex: Int

      val filesToHide = mutableListOf<SwitcherVirtualFile>()
      for (i in selected.indices.reversed()) {
        selectedIndex = selected[i]
        val item = selectedList.model.getElementAt(selectedIndex)

        when (item) {
          is SwitcherVirtualFile -> {
            listModel.remove(item)
            closeEditorForFile(item, project)
            filesToHide.add(item)
          }
          is SwitcherToolWindow -> {
            closeToolWindow(item, project)
          }
          else -> {}
        }
      }

      val currentlyShownFileType = when {
        cbShowOnlyEditedFiles == null -> RecentFileKind.RECENTLY_OPENED_UNPINNED
        cbShowOnlyEditedFiles.isSelected -> RecentFileKind.RECENTLY_EDITED
        else -> RecentFileKind.RECENTLY_OPENED
      }
      if (filesToHide.isNotEmpty()) {
        frontendModel.applyFrontendChanges(currentlyShownFileType, filesToHide.mapNotNull { it.virtualFile }, FileChangeKind.REMOVED)
      }
    }

    private fun scheduleBackendRecentFilesUpdate(request: RecentFilesBackendRequest) {
      modelUpdateScope.launch {
        val isSuccessfulUpdate = remoteApi.updateRecentFilesBackendState(request)
        LOG.debug("Update recent files backend state ${if (isSuccessfulUpdate) "succeeded" else "failed"}")
      }
    }

    fun cancel() {
      popup!!.cancel()
    }

    private fun cancelScheduledUiUpdate() {
      uiUpdateScope.coroutineContext.cancelChildren(CancellationException("UI update cancelled because of adjacent model update"))
    }

    private fun scheduleUiUpdate(update: () -> Unit) {
      cancelScheduledUiUpdate()
      uiUpdateScope.launch(context = Dispatchers.EDT) {
        delay(100.milliseconds)
        update()
      }
    }

    private fun hideSpeedSearchOrPopup() {
      if (speedSearch == null || !speedSearch.isPopupActive) {
        cancel()
      }
      else {
        speedSearch.hidePopup()
      }
    }

    fun go(forward: Boolean) {
      val selected = selectedList
      var list = selected
      var index = list!!.selectedIndex
      if (forward) index++ else index--
      if (forward && index >= list.itemsCount || !forward && index < 0) {
        if (!toolWindows.isEmpty && !files.isEmpty) {
          list = if (list === files) toolWindows else files
        }
        index = if (forward) 0 else list.itemsCount - 1
      }
      list.selectedIndex = index
      list.ensureIndexIsVisible(index)
      if (selected !== list) {
        IdeFocusManager.findInstanceByComponent(list).requestFocus(list, true)
      }
    }

    private val selectedList: JBList<out SwitcherListItem>?
      get() = getSelectedList(files)

    private fun getSelectedList(preferable: JBList<out SwitcherListItem>?): JBList<out SwitcherListItem>? {
      return if (files.hasFocus()) files else if (toolWindows.hasFocus()) toolWindows else preferable
    }

    private fun updateFilesByCheckBox(event: ItemEvent) {
      val onlyEdited = ItemEvent.SELECTED == event.stateChange

      if (onlyEdited) {
        listModel.replaceAll(frontendModel.getRecentFiles(RecentFileKind.RECENTLY_EDITED))
      }
      else {
        listModel.replaceAll(frontendModel.getRecentFiles(RecentFileKind.RECENTLY_OPENED))
      }
      toolWindows.revalidate()
      toolWindows.repaint()
    }

    fun navigate(e: InputEvent?) {
      val mode = if (e == null) FileEditorManagerImpl.OpenMode.DEFAULT else getOpenMode(e)
      val values: List<*> = selectedList!!.selectedValuesList
      val searchQuery = speedSearch?.enteredPrefix

      navigationData = createNavigationData(values)

      cancel()

      val files = values.filterIsInstance<SwitcherVirtualFile>()
      val toolWindow = values.filterIsInstance<SwitcherToolWindow>().firstOrNull()
      val action = values.filterIsInstance<SwitcherRecentLocations>().firstOrNull()
      if (values.isEmpty()) {
        tryToOpenFileSearch(e, searchQuery)
      }
      else if (files.isNotEmpty()) {
        openEditorForFile(files, mode, project)
      }
      else if (toolWindow != null) {
        IdeFocusManager.getInstance(project).doWhenFocusSettlesDown({ openToolWindow(toolWindow, isSpeedSearchPopupActive, project) },
                                                                    ModalityState.current())
      }
      else {
        action?.perform()
      }
    }

    private fun createNavigationData(values: List<*>): SwitcherLogger.NavigationData? {
      if (selectedList != files) return null

      val filteringListModel = files.model as? FilteringListModel<SwitcherListItem> ?: return null
      val collectionListModel = filteringListModel.originalModel as? CollectionListModel<SwitcherListItem> ?: return null
      val originalIndexes = values.filterIsInstance<SwitcherVirtualFile>().map { collectionListModel.getElementIndex(it) }
      val navigatedIndexes = values.filterIsInstance<SwitcherVirtualFile>().map { filteringListModel.getElementIndex(it) }

      return SwitcherLogger.NavigationData(originalIndexes, navigatedIndexes)
    }

    private fun tryToOpenFileSearch(e: InputEvent?, fileName: String?) {
      if (fileName.isNullOrEmpty()) {
        return
      }

      val gotoAction = ActionManager.getInstance().getAction("GotoFile")
      if (gotoAction == null) {
        return
      }

      cancel()
      service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT) {
        val focusDC = DataManager.getInstance().dataContextFromFocusAsync.await()
        val dataContext = CustomizedDataContext.withSnapshot(focusDC) { sink ->
          sink[PlatformDataKeys.PREDEFINED_TEXT] = fileName
        }
        val event = AnActionEvent.createEvent(dataContext, gotoAction.templatePresentation.clone(),
                                              ACTION_PLACE, ActionUiKind.NONE, e)
        ActionUtil.performAction(gotoAction, event)
      }
    }

    private fun registerAction(vararg keys: String, action: (InputEvent?) -> Unit) {
      registerAction(onKeyRelease.getShortcuts(*keys), action)
    }

    private fun registerAction(shortcuts: ShortcutSet, action: (InputEvent?) -> Unit) {
      // ignore an empty shortcut set
      if (shortcuts.shortcuts.isEmpty()) {
        return
      }

      LightEditActionFactory.create { event ->
        if (popup != null && popup.isVisible) action(event.inputEvent)
      }.registerCustomShortcutSet(shortcuts, this, this)
    }

    private fun registerSwingAction(id: @NonNls String, vararg keys: String) {
      registerAction(*keys) { SwingActionDelegate.performAction(id, getSelectedList(null)) }
    }

    private fun registerToolWindowAction(window: SwitcherToolWindow) {
      val mnemonic = window.mnemonic
      if (!mnemonic.isNullOrEmpty()) {
        registerAction(
          when {
            speedSearch == null -> onKeyRelease.getShortcuts(mnemonic)
            SystemInfo.isMac -> CustomShortcutSet.fromString("alt $mnemonic", "alt control $mnemonic")
            else -> CustomShortcutSet.fromString("alt $mnemonic")
          }) {
          cancel()
          window.window.activate(null, true, true)
        }
      }
    }

  }
}

internal fun findAppropriateWindow(window: EditorWindow?): EditorWindow? {
  if (window == null) return null
  if (UISettings.getInstance().editorTabPlacement == UISettings.TABS_NONE) {
    return window.owner.currentWindow
  }
  val windows = window.owner.windows().toList()
  return if (windows.contains(window)) window else windows.firstOrNull()
}

private class SwitcherScrollPane(view: Component, noBorder: Boolean)
  : JBScrollPane(view, VERTICAL_SCROLLBAR_AS_NEEDED, if (noBorder) HORIZONTAL_SCROLLBAR_AS_NEEDED else HORIZONTAL_SCROLLBAR_NEVER) {
  private var width = 0

  init {
    border = if (noBorder) JBUI.Borders.empty() else JBUI.Borders.customLineRight(JBUI.CurrentTheme.Popup.separatorColor())
    viewportBorder = JBUI.Borders.empty()
    minimumSize = JBUI.size(if (noBorder) 250 else 0, 100)
  }

  override fun getPreferredSize(): Dimension {
    val size = super.getPreferredSize()
    if (isPreferredSizeSet) return size
    val min = super.getMinimumSize()
    if (size.width < min.width) size.width = min.width
    if (size.height < min.height) size.height = min.height
    if (HORIZONTAL_SCROLLBAR_NEVER != getHorizontalScrollBarPolicy()) return size
    width = max(size.width, width)
    size.width = width
    return size
  }
}

private val LOG by lazy { fileLogger() }