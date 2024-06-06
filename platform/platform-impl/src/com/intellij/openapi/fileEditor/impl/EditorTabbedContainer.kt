// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.DataManager
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.CloseAction.CloseTarget
import com.intellij.ide.actions.MaximizeEditorInSplitAction
import com.intellij.ide.actions.ShowFilePathAction
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.FileDropManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.containsFileDropTargets
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorTabbedContainer.DockableEditor
import com.intellij.openapi.fileEditor.impl.EditorWindow.Companion.DRAG_START_INDEX_KEY
import com.intellij.openapi.fileEditor.impl.EditorWindow.Companion.DRAG_START_LOCATION_HASH_KEY
import com.intellij.openapi.fileEditor.impl.EditorWindow.Companion.DRAG_START_PINNED_KEY
import com.intellij.openapi.fileEditor.impl.tabActions.CloseTab
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.*
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockManager
import com.intellij.ui.docking.DockableContent
import com.intellij.ui.docking.DragSession
import com.intellij.ui.docking.impl.DockManagerImpl
import com.intellij.ui.docking.impl.DockManagerImpl.Companion.isNorthPanelAvailable
import com.intellij.ui.docking.impl.DockManagerImpl.Companion.isNorthPanelVisible
import com.intellij.ui.docking.impl.DockManagerImpl.Companion.isSingletonEditorInWindow
import com.intellij.ui.tabs.*
import com.intellij.ui.tabs.TabInfo.DragOutDelegate
import com.intellij.ui.tabs.UiDecorator.UiDecoration
import com.intellij.ui.tabs.impl.*
import com.intellij.ui.tabs.impl.SingleHeightTabs.Companion.UNSCALED_PREF_HEIGHT
import com.intellij.ui.tabs.impl.TabLabel.ActionsPosition
import com.intellij.ui.tabs.impl.multiRow.CompressibleMultiRowLayout
import com.intellij.ui.tabs.impl.multiRow.MultiRowLayout
import com.intellij.ui.tabs.impl.multiRow.ScrollableMultiRowLayout
import com.intellij.ui.tabs.impl.multiRow.WrapMultiRowLayout
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.TimedDeadzone
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.TimeUnit
import java.util.function.Function
import javax.swing.*

class EditorTabbedContainer internal constructor(
  private val window: EditorWindow,
  private val coroutineScope: CoroutineScope,
) : CloseTarget {
  @JvmField
  internal val editorTabs: JBEditorTabs
  private val dragOutDelegate: DragOutDelegate

  init {
    val disposable = Disposer.newDisposable()
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      Disposer.dispose(disposable)
    }

    editorTabs = EditorTabs(coroutineScope = coroutineScope, parentDisposable = disposable, window = window)
    dragOutDelegate = EditorTabbedContainerDragOutDelegate(window = window, editorTabs = editorTabs)

    val project = window.manager.project
    project.messageBus.connect(coroutineScope).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        editorTabs.updateActive()
      }

      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        editorTabs.updateActive()
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        editorTabs.updateActive()
      }
    })
    editorTabs.component.isFocusable = false
    editorTabs.component.transferHandler = EditorTabbedContainerTransferHandler(window)
    editorTabs.setDataProvider(object : EdtCompatibleDataProvider {
      override fun uiDataSnapshot(sink: DataSink) {
        sink[CommonDataKeys.PROJECT] = window.manager.project
        sink[CommonDataKeys.VIRTUAL_FILE] = window.selectedComposite?.file
        sink[EditorWindow.DATA_KEY] = window
        sink[PlatformCoreDataKeys.FILE_EDITOR] = window.selectedComposite?.selectedEditor
        sink[PlatformCoreDataKeys.HELP_ID] = "ideaInterface.editor"
        sink[CloseTarget.KEY] = if (editorTabs.selectedInfo == null) null else this@EditorTabbedContainer
      }
    })
      .setPopupGroup(
        /* popupGroup = */ { CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_TAB_POPUP) as ActionGroup },
        /* place = */ ActionPlaces.EDITOR_TAB_POPUP,
        /* addNavigationGroup = */ false
      )
      .addTabMouseListener(TabMouseListener(window = window, editorTabs = editorTabs)).presentation
      .setTabDraggingEnabled(true)
      .setTabLabelActionsMouseDeadzone(TimedDeadzone.NULL).setTabLabelActionsAutoHide(false)
      .setActiveTabFillIn(EditorColorsManager.getInstance().globalScheme.defaultBackground).setPaintFocus(false).jbTabs
      .addListener(object : TabsListener {
        override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
          val oldEditor = oldSelection?.let { window.manager.getSelectedEditor((it.`object` as VirtualFile)) }
          oldEditor?.deselectNotify()
          val newFile = (newSelection ?: return).`object` as VirtualFile
          val newEditor = newFile.let { window.manager.getSelectedEditor(newFile) }
          newEditor?.selectNotify()
          if (GeneralSettings.getInstance().isSyncOnFrameActivation) {
            VfsUtil.markDirtyAndRefresh(true, false, false, newFile)
          }
        }
      })
      .setSelectionChangeHandler { _, _, doChangeSelection ->
        if (window.isDisposed) {
          return@setSelectionChangeHandler ActionCallback.DONE
        }
        val result = ActionCallback()
        CommandProcessor.getInstance().executeCommand(project, {
          IdeDocumentHistory.getInstance(project).onSelectionChanged()
          result.notify(doChangeSelection.run())
        }, "EditorChange", null)
        result
      }
    editorTabs.presentation.setRequestFocusOnLastFocusedComponent(true)
    editorTabs.component.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (editorTabs.findInfo(e) != null || window.owner.isFloating) {
          return
        }
        if (!e.isPopupTrigger && SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
          doProcessDoubleClick(e = e, editorTabs = editorTabs, window = window)
        }
      }
    })
    setTabPlacement(UISettings.getInstance().editorTabPlacement)
  }

  val tabCount: Int
    get() = editorTabs.tabCount

  fun setSelectedIndex(indexToSelect: Int): ActionCallback {
    if (indexToSelect >= editorTabs.tabCount) {
      return ActionCallback.REJECTED
    }
    else {
      return editorTabs.select(info = editorTabs.getTabAt(indexToSelect), requestFocus = true)
    }
  }

  val component: JComponent
    get() = editorTabs.component

  fun removeTabAt(componentIndex: Int, indexToSelect: Int) {
    var toSelect = if (indexToSelect >= 0 && indexToSelect < editorTabs.tabCount) editorTabs.getTabAt(indexToSelect) else null
    val info = editorTabs.getTabAt(componentIndex)
    // removing the hidden tab happens at the end of the drag-out, we've already selected the correct tab for this case in dragOutStarted
    if (info.isHidden || !window.manager.project.isOpen || window.isDisposed) {
      toSelect = null
    }
    editorTabs.removeTab(info, toSelect)
  }

  val selectedIndex: Int
    get() = editorTabs.selectedInfo?.let { editorTabs.getIndexOf(it) } ?: -1

  fun setForegroundAt(index: Int, color: Color) {
    editorTabs.getTabAt(index).setDefaultForeground(color)
  }

  fun setTextAttributes(index: Int, attributes: TextAttributes?) {
    editorTabs.getTabAt(index).setDefaultAttributes(attributes)
  }

  fun setTabLayoutPolicy(policy: Int) {
    when (policy) {
      JTabbedPane.SCROLL_TAB_LAYOUT -> editorTabs.presentation.setSingleRow(true)
      JTabbedPane.WRAP_TAB_LAYOUT -> editorTabs.presentation.setSingleRow(false)
      else -> throw IllegalArgumentException("Unsupported tab layout policy: $policy")
    }
  }

  fun setTabPlacement(tabPlacement: Int) {
    when (tabPlacement) {
      SwingConstants.TOP -> editorTabs.presentation.setTabsPosition(JBTabsPosition.top)
      SwingConstants.BOTTOM -> editorTabs.presentation.setTabsPosition(JBTabsPosition.bottom)
      SwingConstants.LEFT -> editorTabs.presentation.setTabsPosition(JBTabsPosition.left)
      SwingConstants.RIGHT -> editorTabs.presentation.setTabsPosition(JBTabsPosition.right)
      UISettings.TABS_NONE -> editorTabs.presentation.isHideTabs = true
      else -> throw IllegalArgumentException("Unknown tab placement code=$tabPlacement")
    }
  }

  /**
   * @param ignorePopup if `false` and a context menu is shown currently for some tab, component for which a menu is invoked will be returned
   */
  fun getSelectedComponent(ignorePopup: Boolean): Any? {
    return (if (ignorePopup) editorTabs.selectedInfo else editorTabs.targetInfo)?.component
  }

  internal fun insertTab(
    file: VirtualFile,
    icon: Icon?,
    component: JComponent,
    tooltip: @NlsContexts.Tooltip String?,
    indexToInsert: Int,
    selectedEditor: FileEditor?,
    parentDisposable: Disposable,
  ): TabInfo {
    editorTabs.findInfo(file)?.let {
      return it
    }

    val tab = createTabInfo(
      component = component,
      file = file,
      icon = icon,
      tooltip = tooltip,
      parentDisposable = parentDisposable,
      selectedEditor = selectedEditor,
      coroutineScope = coroutineScope,
      window = window,
      editorActionGroup = ActionManager.getInstance().getAction("EditorTabActionGroup"),
    )

    coroutineScope.launch {
      val title = EditorTabPresentationUtil.getEditorTabTitle(window.manager.project, file)
      withContext(Dispatchers.EDT) {
        tab.setText(title)
      }
    }

    tab.setDragOutDelegate(dragOutDelegate)

    editorTabs.addTabSilently(info = tab, index = indexToInsert)
    return tab
  }

  internal fun setTabs(tabs: List<TabInfo>) {
    editorTabs.setTabs(tabs)
    for (tab in tabs) {
      tab.setDragOutDelegate(dragOutDelegate)
    }
  }

  val tabs: JBTabs
    get() = editorTabs

  override fun close() {
    val selected = editorTabs.targetInfo ?: return
    window.manager.closeFile((selected.`object` as VirtualFile), window)
  }

  class DockableEditor(
    val img: Image?,
    val file: VirtualFile,
    private val presentation: Presentation,
    private val preferredSize: Dimension,
    val isPinned: Boolean,
    val isNorthPanelAvailable: Boolean,
  ) : DockableContent<VirtualFile?> {
    constructor(
      img: Image,
      file: VirtualFile,
      presentation: Presentation,
      preferredSize: Dimension,
      isFilePinned: Boolean,
    ) : this(
      img = img,
      file = file,
      presentation = presentation,
      preferredSize = preferredSize,
      isPinned = isFilePinned,
      isNorthPanelAvailable = isNorthPanelVisible(UISettings.getInstance()),
    )

    override fun getKey(): VirtualFile = file

    override fun getPreviewImage(): Image? = img

    override fun getPreferredSize(): Dimension = preferredSize

    override fun getDockContainerType(): String = DockableEditorContainerFactory.TYPE

    override fun getPresentation(): Presentation = presentation

    override fun close() {}
  }
}

private fun doProcessDoubleClick(e: MouseEvent, editorTabs: JBTabsImpl, window: EditorWindow) {
  val info = editorTabs.findInfo(e)
  if (info != null) {
    val composite = (info.component as EditorCompositePanel).composite
    if (composite.isPreview) {
      composite.isPreview = false
      window.owner.scheduleUpdateFileColor(composite.file)
      return
    }
  }

  if (!AdvancedSettings.getBoolean("editor.maximize.on.double.click") &&
      !AdvancedSettings.getBoolean("editor.maximize.in.splits.on.double.click")) {
    return
  }

  val actionManager = ActionManager.getInstance()
  @Suppress("DEPRECATION")
  val context = DataManager.getInstance().dataContext
  var isEditorMaximized: Boolean? = null
  var areAllToolWindowsHidden: Boolean? = null
  if (AdvancedSettings.getBoolean("editor.maximize.in.splits.on.double.click")) {
    val maximizeEditorInSplit = actionManager.getAction("MaximizeEditorInSplit")
    if (maximizeEditorInSplit != null) {
      val event = AnActionEvent(e, context, ActionPlaces.EDITOR_TAB, Presentation(), actionManager, e.modifiersEx)
      maximizeEditorInSplit.update(event)
      isEditorMaximized = event.presentation.getClientProperty(MaximizeEditorInSplitAction.CURRENT_STATE_IS_MAXIMIZED_KEY)
    }
  }

  if (AdvancedSettings.getBoolean("editor.maximize.on.double.click")) {
    val hideAllToolWindows = actionManager.getAction("HideAllWindows")
    if (hideAllToolWindows != null) {
      val event = AnActionEvent(e, context, ActionPlaces.EDITOR_TAB, Presentation(), actionManager, e.modifiersEx)
      hideAllToolWindows.update(event)
      areAllToolWindowsHidden = event.presentation.getClientProperty(MaximizeEditorInSplitAction.CURRENT_STATE_IS_MAXIMIZED_KEY)
    }
  }

  @Suppress("SpellCheckingInspection")
  val runnable = if (Registry.`is`("editor.position.mouse.cursor.on.doubleclicked.tab")) createKeepMousePositionRunnable(e) else null
  if (areAllToolWindowsHidden != null && (isEditorMaximized == null || isEditorMaximized === areAllToolWindowsHidden)) {
    actionManager.tryToExecute(actionManager.getAction("HideAllWindows"), e, null, ActionPlaces.EDITOR_TAB, true)
  }
  if (isEditorMaximized != null) {
    actionManager.tryToExecute(actionManager.getAction("MaximizeEditorInSplit"), e, null, ActionPlaces.EDITOR_TAB, true)
  }
  runnable?.invoke()
}

private fun createKeepMousePositionRunnable(event: MouseEvent): () -> Unit {
  return {
    EdtScheduledExecutorService.getInstance().schedule({
                                                         val component = event.component
                                                         if (component != null && component.isShowing) {
                                                           val p = component.locationOnScreen
                                                           p.translate(event.x, event.y)
                                                           try {
                                                             Robot().mouseMove(p.x, p.y)
                                                           }
                                                           catch (ignored: AWTException) {
                                                           }
                                                         }
                                                       }, 50, TimeUnit.MILLISECONDS)
  }
}

private class TabMouseListener(private val window: EditorWindow, private val editorTabs: JBTabsImpl) : MouseAdapter() {
  private var actionClickCount = 0

  override fun mouseReleased(e: MouseEvent) {
    if (!UIUtil.isCloseClick(e, MouseEvent.MOUSE_RELEASED)) {
      return
    }

    val info = editorTabs.findInfo(e) ?: return
    IdeEventQueue.getInstance().blockNextEvents(e)
    if (e.isAltDown && e.button == MouseEvent.BUTTON1) { //close others
      val allTabInfos = editorTabs.tabs
      for (tabInfo in allTabInfos) {
        if (tabInfo == info) {
          continue
        }
        window.manager.closeFile((tabInfo.`object` as VirtualFile), window)
      }
    }
    else {
      window.manager.closeFile((info.`object` as VirtualFile), window)
    }
  }

  override fun mousePressed(e: MouseEvent) {
    if (UIUtil.isActionClick(e)) {
      if (e.clickCount == 1) {
        actionClickCount = 0
      }
      // clicks on the close window button don't count in determining whether we have a double click on the tab (IDEA-70403)
      val deepestComponent = SwingUtilities.getDeepestComponentAt(e.component, e.x, e.y)
      if (deepestComponent !is InplaceButton) {
        actionClickCount++
      }
      if (actionClickCount > 1 && actionClickCount % 2 == 0) {
        doProcessDoubleClick(e = e, editorTabs = editorTabs, window = window)
      }
    }
  }

  override fun mouseClicked(e: MouseEvent) {
    if (UIUtil.isActionClick(e, MouseEvent.MOUSE_CLICKED) && (e.isMetaDown || !SystemInfoRt.isMac && e.isControlDown)) {
      val o = editorTabs.findInfo(e)?.`object`
      if (o is VirtualFile) {
        ShowFilePathAction.show((o as VirtualFile?)!!, e)
      }
    }
  }
}

internal class EditorTabbedContainerDragOutDelegate(private val window: EditorWindow, private val editorTabs: JBTabsImpl) : DragOutDelegate {
  private var file: VirtualFile? = null
  private var session: DragSession? = null

  override fun dragOutStarted(mouseEvent: MouseEvent, info: TabInfo) {
    val previousSelection = info.previousSelection ?: editorTabs.getToSelectOnRemoveOf(info)
    val img = JBTabsImpl.getComponentImage(info)

    val dragStartIndex = editorTabs.getIndexOf(info)
    val isPinnedAtStart = info.isPinned
    info.isHidden = true
    if (previousSelection != null) {
      editorTabs.select(previousSelection, true)
    }

    val file = info.`object` as VirtualFile
    this.file = file
    file.putUserData(DRAG_START_INDEX_KEY, dragStartIndex)
    file.putUserData(DRAG_START_LOCATION_HASH_KEY, System.identityHashCode(editorTabs))
    file.putUserData(DRAG_START_PINNED_KEY, isPinnedAtStart)
    val presentation = Presentation(info.text)
    if (DockManagerImpl.REOPEN_WINDOW.isIn(file)) {
      presentation.putClientProperty(DockManagerImpl.REOPEN_WINDOW, DockManagerImpl.REOPEN_WINDOW.get(file, true))
    }
    presentation.icon = info.icon
    val editors = window.getComposite(file)?.allEditors ?: emptyList()
    presentation.putClientProperty(DockManagerImpl.ALLOW_DOCK_TOOL_WINDOWS, !isSingletonEditorInWindow(editors))
    session = DockManager.getInstance(window.manager.project).createDragSession(
      mouseEvent,
      createDockableEditor(
        image = img,
        file = file,
        presentation = presentation,
        window = window,
        isNorthPanelAvailable = isNorthPanelAvailable(editors),
      ),
    )
  }

  override fun processDragOut(event: MouseEvent, source: TabInfo) {
    session!!.process(event)
  }

  override fun dragOutFinished(event: MouseEvent, source: TabInfo) {
    val copy = UIUtil.isControlKeyDown(event) || session!!.getResponse(event) == DockContainer.ContentResponse.ACCEPT_COPY
    if (copy) {
      source.isHidden = false
    }
    else {
      file!!.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, true)
      window.manager.closeFile(file!!, window)
    }
    session!!.process(event)
    if (!copy) {
      file!!.putUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN, null)
    }
    file = null
    session = null
  }

  override fun dragOutCancelled(source: TabInfo) {
    source.isHidden = false
    session?.let {
      it.cancel()
      session = null
    }
    file = null
  }
}

internal fun createTabInfo(
  component: JComponent,
  file: VirtualFile,
  icon: Icon?,
  tooltip: @NlsContexts.Tooltip String?,
  parentDisposable: Disposable,
  selectedEditor: FileEditor?,
  coroutineScope: CoroutineScope,
  window: EditorWindow,
  editorActionGroup: AnAction,
): TabInfo {
  val tab = TabInfo(component)
    .setText(file.presentableName)
    .setIcon(if (UISettings.getInstance().showFileIconInTabs) icon else null)
    .setTooltipText(tooltip)
    .setObject(file)
  tab.setTestableUi { it.put("editorTab", tab.text) }

  val project = window.manager.project
  coroutineScope.launch {
    val color = readAction { EditorTabPresentationUtil.getEditorTabBackgroundColor(project, file) }
    withContext(Dispatchers.EDT) {
      tab.setTabColor(color)
    }
  }

  val closeTab = CloseTab(component = component, file = file, editorWindow = window, parentDisposable = parentDisposable)
  val group = DefaultActionGroup(editorActionGroup, closeTab)
  tab.setTabLabelActions(group, ActionPlaces.EDITOR_TAB)
  selectedEditor?.tabActions?.let {
    tab.setTabPaneActions(it)
  }
  return tab
}

private class EditorTabbedContainerTransferHandler(private val window: EditorWindow) : TransferHandler() {
  override fun importData(comp: JComponent, t: Transferable): Boolean {
    if (containsFileDropTargets(t.transferDataFlavors)) {
      window.manager.project.service<FileDropManager>().scheduleDrop(transferable = t, editor = null, editorWindowCandidate = window)
      return true
    }
    return false
  }

  override fun canImport(comp: JComponent, transferFlavors: Array<DataFlavor>): Boolean = containsFileDropTargets(transferFlavors)
}

private class EditorTabs(
  coroutineScope: CoroutineScope,
  parentDisposable: Disposable,
  private val window: EditorWindow,
) : JBEditorTabs(
  project = window.manager.project,
  parentDisposable = parentDisposable,
  coroutineScope = window.coroutineScope,
), ComponentWithMnemonics, EditorWindowHolder, DataProvider {
  private val _entryPointActionGroup: DefaultActionGroup
  private var isActive = false

  init {
    val listener = AWTEventListener { updateActive() }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.FOCUS_EVENT_MASK)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
    }
    setUiDecoratorWithoutApply(object : UiDecorator {
      override fun getDecoration(): UiDecoration {
        return UiDecoration(
          labelInsets = getTabLabelInsets(),
          contentInsetsSupplier = Function { position ->
            JBUI.CurrentTheme.EditorTabs.tabContentInsets(when (position) {
                                                            ActionsPosition.RIGHT -> true
                                                            ActionsPosition.LEFT -> false
                                                            ActionsPosition.NONE -> null
                                                          })
          },
          iconTextGap = JBUI.scale(4)
        )
      }
    })

    val source = ActionManager.getInstance().getAction("EditorTabsEntryPoint")
    source.templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
    _entryPointActionGroup = DefaultActionGroup(java.util.List.of(source))
  }

  override fun getData(dataId: String): Any? {
    return when {
      CommonDataKeys.PROJECT.`is`(dataId) -> window.owner.manager.project
      EditorWindow.DATA_KEY.`is`(dataId) -> window
      PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR.`is`(dataId) -> window.owner.currentCompositeFlow.value?.selectedEditor
      else -> null
    }
  }

  override fun getEditorWindow(): EditorWindow = window

  override fun useMultiRowLayout(): Boolean {
    return !isSingleRow || (isHorizontalTabs && (TabLayout.showPinnedTabsSeparately() || !UISettings.getInstance().hideTabsIfNeeded))
  }

  override fun createSingleRowLayout(): SingleRowLayout {
    return ScrollableSingleRowLayout(this, ExperimentalUI.isEditorTabsWithScrollBar)
  }

  override fun createMultiRowLayout(): MultiRowLayout {
    return when {
      !isSingleRow -> WrapMultiRowLayout(this, TabLayout.showPinnedTabsSeparately())
      UISettings.getInstance().hideTabsIfNeeded -> ScrollableMultiRowLayout(
        tabs = this,
        showPinnedTabsSeparately = true,
        isWithScrollBar = ExperimentalUI.isEditorTabsWithScrollBar,
      )
      else -> CompressibleMultiRowLayout(this, TabLayout.showPinnedTabsSeparately())
    }
  }

  override fun paintChildren(g: Graphics) {
    super.paintChildren(g)
    drawBorder(g)
  }

  override fun shouldPaintBottomBorder(): Boolean {
    val tab = selectedInfo ?: return true
    return !(tab.component as EditorCompositePanel).composite.selfBorder
  }

  // return same instance to avoid unnecessary action toolbar updates
  override val entryPointActionGroup: DefaultActionGroup
    get() = _entryPointActionGroup

  fun getTabLabelInsets(): JBInsets {
    val insets = if (isHorizontalTabs) JBUI.CurrentTheme.EditorTabs.tabInsets() else JBUI.CurrentTheme.EditorTabs.verticalTabInsets()
    return insets as? JBInsets ?: error("JBInsets expected, but was: $insets")
  }

  override fun createTabLabel(info: TabInfo): TabLabel = EditorTabLabel(info = info, tabs = this)

  override fun getTabActionIcon(info: TabInfo, isHovered: Boolean): Icon? {
    if (!tabs.contains(info)) {
      // can be requested right after the tab is removed, return null in this case
      return null
    }

    val closeTabAction = info.tabLabelActions?.getChildren(null)?.lastOrNull() as? CloseTab
    return closeTabAction?.getIcon(isHovered)
  }

  override fun createTabPainterAdapter(): TabPainterAdapter = EditorTabPainterAdapter()

  override fun createTabBorder(): JBTabsBorder = JBEditorTabsBorder(this)

  override fun select(info: TabInfo, requestFocus: Boolean): ActionCallback {
    isActive = true
    return super.select(info, requestFocus)
  }

  fun updateActive() {
    checkActive()
    SwingUtilities.invokeLater { checkActive() }
  }

  private fun checkActive() {
    val newActive = UIUtil.isFocusAncestor(this)
    if (newActive != isActive) {
      isActive = newActive
      revalidateAndRepaint()
    }
  }

  override fun isActiveTabs(info: TabInfo?): Boolean = isActive

  override fun getToSelectOnRemoveOf(tab: TabInfo): TabInfo? {
    if (window.isDisposed) {
      return null
    }

    val index = getIndexOf(tab)
    if (index != -1) {
      val file = tab.composite.file
      val indexToSelect = window.computeIndexToSelect(file, index)
      if (indexToSelect >= 0 && indexToSelect < tabs.size) {
        return getTabAt(indexToSelect)
      }
    }
    return super.getToSelectOnRemoveOf(tab)
  }

  override fun revalidateAndRepaint(layoutNow: Boolean) {
    // called from super constructor
    @Suppress("SENSELESS_COMPARISON")
    if (window != null && !window.owner.isInsideChange) {
      super.revalidateAndRepaint(layoutNow)
    }
  }
}

private class EditorTabLabel(info: TabInfo, tabs: JBTabsImpl) : TabLabel(tabs, info) {
  init {
    updateFont()
  }

  override fun getPreferredSize(): Dimension {
    return Dimension(super.getPreferredSize().width, getPreferredHeight())
  }

  override fun updateUI() {
    super.updateUI()
    updateFont()
  }

  private fun updateFont() {
    if (ExperimentalUI.isNewUI()) {
      val font = JBUI.CurrentTheme.EditorTabs.font()
      GuiUtils.iterateChildren(this, { c ->
        c.font = font
      })
    }
  }

  private fun getPreferredHeight(): Int {
    val insets = (tabs as EditorTabs).getTabLabelInsets().unscaled
    val height = JBUI.scale(UNSCALED_PREF_HEIGHT - insets.top - insets.bottom)
    val layoutInsets = tabs.layoutInsets
    return height - layoutInsets.top - layoutInsets.bottom
  }

  override val isShowTabActions: Boolean
    get() = isPinned || UISettings.getInstance().showCloseButton

  override val isTabActionsOnTheRight: Boolean
    get() = UISettings.getInstance().closeTabButtonOnTheRight

  override fun shouldPaintFadeout(): Boolean = super.shouldPaintFadeout() && Registry.`is`("ide.editor.tabs.show.fadeout", true)

  override fun editLabelForeground(baseForeground: Color?): Color? {
    if (baseForeground != null && paintDimmed()) {
      val blendValue = JBUI.CurrentTheme.EditorTabs.unselectedBlend()
      return ColorUtil.blendColorsInRgb(effectiveBackground, baseForeground, blendValue.toDouble())
    }
    else {
      return baseForeground
    }
  }

  override fun getIconAlpha(): Float = if (paintDimmed()) JBUI.CurrentTheme.EditorTabs.unselectedAlpha() else 1f

  private fun paintDimmed() = ExperimentalUI.isNewUI() && tabs.selectedInfo != info && !tabs.isHoveredTab(this)
}

internal fun createDockableEditor(
  image: Image?,
  file: VirtualFile?,
  presentation: Presentation,
  window: EditorWindow,
  isNorthPanelAvailable: Boolean,
): DockableEditor {
  return DockableEditor(
    img = image,
    file = file!!,
    presentation = presentation,
    preferredSize = window.size,
    isPinned = window.isFilePinned(file),
    isNorthPanelAvailable = isNorthPanelAvailable,
  )
}