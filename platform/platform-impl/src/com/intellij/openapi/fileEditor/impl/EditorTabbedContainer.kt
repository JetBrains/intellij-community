// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

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
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorWindow.Companion.DRAG_START_INDEX_KEY
import com.intellij.openapi.fileEditor.impl.EditorWindow.Companion.DRAG_START_LOCATION_HASH_KEY
import com.intellij.openapi.fileEditor.impl.EditorWindow.Companion.DRAG_START_PINNED_KEY
import com.intellij.openapi.fileEditor.impl.tabActions.CloseTab
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.fileEditor.impl.text.FileDropHandler
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
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
import com.intellij.ui.tabs.impl.TabLabel.ActionsPosition
import com.intellij.ui.tabs.impl.multiRow.CompressibleMultiRowLayout
import com.intellij.ui.tabs.impl.multiRow.MultiRowLayout
import com.intellij.ui.tabs.impl.multiRow.ScrollableMultiRowLayout
import com.intellij.ui.tabs.impl.multiRow.WrapMultiRowLayout
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.lang.Runnable
import java.util.concurrent.TimeUnit
import java.util.function.Function
import javax.swing.*

class EditorTabbedContainer internal constructor(private val window: EditorWindow,
                                                 private val coroutineScope: CoroutineScope) : CloseTarget {
  private val editorTabs: EditorTabs
  private val dragOutDelegate = MyDragOutDelegate()

  init {
    val disposable = Disposer.newDisposable()
    coroutineScope.coroutineContext.job.invokeOnCompletion { Disposer.dispose(disposable) }

    editorTabs = EditorTabs(coroutineScope = coroutineScope, parentDisposable = disposable, window = window)
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
    editorTabs.component.transferHandler = MyTransferHandler()
    editorTabs
      .setDataProvider { dataId ->
        when {
          CommonDataKeys.PROJECT.`is`(dataId) -> window.manager.project
          CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> window.getContextComposite()?.file?.takeIf { it.isValid }
          EditorWindow.DATA_KEY.`is`(dataId) -> window
          PlatformCoreDataKeys.FILE_EDITOR.`is`(dataId) -> window.getContextComposite()?.selectedEditor
          PlatformCoreDataKeys.HELP_ID.`is`(dataId) -> HELP_ID
          CloseTarget.KEY.`is`(dataId) -> if (editorTabs.selectedInfo == null) null else this@EditorTabbedContainer
          else -> null
        }
      }
      .setPopupGroup(
        /* popupGroup = */ { CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_TAB_POPUP) as ActionGroup },
        /* place = */ ActionPlaces.EDITOR_TAB_POPUP,
        /* addNavigationGroup = */ false
      )
      .addTabMouseListener(TabMouseListener()).presentation
      .setTabDraggingEnabled(true)
      .setTabLabelActionsMouseDeadzone(TimedDeadzone.NULL).setTabLabelActionsAutoHide(false)
      .setActiveTabFillIn(EditorColorsManager.getInstance().globalScheme.defaultBackground).setPaintFocus(false).jbTabs
      .addListener(object : TabsListener {
        override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
          val oldEditor = if (oldSelection == null) null else window.manager.getSelectedEditor((oldSelection.getObject() as VirtualFile))
          oldEditor?.deselectNotify()
          val newFile = (newSelection ?: return).getObject() as VirtualFile
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
          (IdeDocumentHistory.getInstance(project) as IdeDocumentHistoryImpl).onSelectionChanged()
          result.notify(doChangeSelection.run())
        }, "EditorChange", null)
        result
      }
    editorTabs.presentation.setRequestFocusOnLastFocusedComponent(true)
    editorTabs.component.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (editorTabs.findInfo(e) != null || isFloating) {
          return
        }
        if (!e.isPopupTrigger && SwingUtilities.isLeftMouseButton(e) && e.clickCount == 2) {
          doProcessDoubleClick(e)
        }
      }
    })
    setTabPlacement(UISettings.getInstance().editorTabPlacement)
  }

  companion object {
    const val HELP_ID: @NonNls String = "ideaInterface.editor"

    internal fun createDockableEditor(image: Image?,
                                      file: VirtualFile?,
                                      presentation: Presentation,
                                      window: EditorWindow,
                                      isNorthPanelAvailable: Boolean): DockableEditor {
      return DockableEditor(image, file!!, presentation, window.size, window.isFilePinned(file), isNorthPanelAvailable)
    }

    private fun createKeepMousePositionRunnable(event: MouseEvent): Runnable {
      return Runnable {
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
  }

  val tabCount: Int
    get() = editorTabs.tabCount

  fun setSelectedIndex(indexToSelect: Int): ActionCallback {
    return setSelectedIndex(indexToSelect = indexToSelect, focusEditor = true)
  }

  fun setSelectedIndex(indexToSelect: Int, focusEditor: Boolean): ActionCallback {
    return if (indexToSelect >= editorTabs.tabCount) {
      ActionCallback.REJECTED
    }
    else {
      editorTabs.select(editorTabs.getTabAt(indexToSelect), focusEditor)
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
    get() = editorTabs.getIndexOf(editorTabs.selectedInfo)

  fun setForegroundAt(index: Int, color: Color) {
    editorTabs.getTabAt(index).setDefaultForeground(color)
  }

  fun setTextAttributes(index: Int, attributes: TextAttributes?) {
    val tab = editorTabs.getTabAt(index)
    tab.setDefaultAttributes(attributes)
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

  fun getSelectedComposite(): EditorComposite? {
    val selectedInfo = editorTabs.selectedInfo
    return selectedInfo?.component?.let { (it as EditorWindowTopComponent).composite }
  }

  fun insertTab(file: VirtualFile,
                icon: Icon?,
                component: JComponent,
                tooltip: @NlsContexts.Tooltip String?,
                indexToInsert: Int,
                composite: EditorComposite,
                parentDisposable: Disposable) {
    val existing = editorTabs.findInfo(file)
    if (existing != null) {
      return
    }

    val project = window.manager.project
    val tab = TabInfo(component)
      .setText(file.presentableName)
      .setIcon(if (UISettings.getInstance().showFileIconInTabs) icon else null)
      .setTooltipText(tooltip)
      .setObject(file)
      .setDragOutDelegate(dragOutDelegate)
    tab.setTestableUi { it.put("editorTab", tab.text) }

    coroutineScope.launch {
      val title = readAction { EditorTabPresentationUtil.getEditorTabTitle(project, file) }
      withContext(Dispatchers.EDT) {
        tab.text = title
      }
    }
    coroutineScope.launch {
      val color = readAction { EditorTabPresentationUtil.getEditorTabBackgroundColor(project, file) }
      withContext(Dispatchers.EDT) {
        tab.tabColor = color
      }
    }

    val closeTab = CloseTab(component, file, window, parentDisposable)
    val editorActionGroup = ActionManager.getInstance().getAction("EditorTabActionGroup")
    val group = DefaultActionGroup(editorActionGroup, closeTab)
    tab.setTabLabelActions(group, ActionPlaces.EDITOR_TAB)
    tab.setTabPaneActions(composite.selectedEditor!!.tabActions)
    if (AsyncEditorLoader.isOpenedInBulk(file)) {
      editorTabs.addTabWithoutUpdating(info = tab, index = indexToInsert, isDropTarget = false)
      editorTabs.updateListeners()
    }
    else {
      editorTabs.addTabSilently(tab, indexToInsert)
    }
  }

  val isEmptyVisible: Boolean
    get() = editorTabs.isEmptyVisible
  val tabs: JBTabs
    get() = editorTabs

  fun requestFocus(forced: Boolean) {
    IdeFocusManager.getInstance(window.manager.project).requestFocus(editorTabs.component, forced)
  }

  override fun close() {
    val selected = editorTabs.targetInfo ?: return
    window.manager.closeFile((selected.getObject() as VirtualFile), window)
  }

  private val isFloating: Boolean
    get() = window.owner.isFloating

  private inner class TabMouseListener : MouseAdapter() {
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
          window.manager.closeFile((tabInfo.getObject() as VirtualFile), window)
        }
      }
      else {
        window.manager.closeFile((info.getObject() as VirtualFile), window)
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
          doProcessDoubleClick(e)
        }
      }
    }

    override fun mouseClicked(e: MouseEvent) {
      if (UIUtil.isActionClick(e, MouseEvent.MOUSE_CLICKED) && (e.isMetaDown || !SystemInfoRt.isMac && e.isControlDown)) {
        val o = editorTabs.findInfo(e)?.getObject()
        if (o is VirtualFile) {
          ShowFilePathAction.show((o as VirtualFile?)!!, e)
        }
      }
    }
  }

  private fun doProcessDoubleClick(e: MouseEvent) {
    val info = editorTabs.findInfo(e)
    if (info != null) {
      val composite = (info.component as EditorWindowTopComponent).composite
      if (composite.isPreview) {
        composite.isPreview = false
        window.owner.updateFileColorAsync(composite.file)
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
    runnable?.run()
  }

  internal inner class MyDragOutDelegate : DragOutDelegate {
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

      val file = info.getObject() as VirtualFile
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
      val isNorthPanelAvailable = isNorthPanelAvailable(editors)
      presentation.putClientProperty(DockManagerImpl.ALLOW_DOCK_TOOL_WINDOWS, !isSingletonEditorInWindow(editors))
      session = dockManager.createDragSession(mouseEvent, createDockableEditor(img, file, presentation, window, isNorthPanelAvailable))
    }

    private val dockManager: DockManager
      get() = DockManager.getInstance(window.manager.project)

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

  class DockableEditor(val img: Image?,
                       val file: VirtualFile,
                       private val presentation: Presentation,
                       private val preferredSize: Dimension,
                       val isPinned: Boolean,
                       val isNorthPanelAvailable: Boolean) : DockableContent<VirtualFile?> {

    @Suppress("UNUSED_PARAMETER")
    @Deprecated("project is not required.")
    constructor(project: Project?,
                img: Image,
                file: VirtualFile,
                presentation: Presentation,
                preferredSize: Dimension,
                isFilePinned: Boolean) : this(img = img,
                                              file = file,
                                              presentation = presentation,
                                              preferredSize = preferredSize,
                                              isPinned = isFilePinned,
                                              isNorthPanelAvailable = isNorthPanelVisible(UISettings.getInstance()))

    constructor(img: Image,
                file: VirtualFile,
                presentation: Presentation,
                preferredSize: Dimension,
                isFilePinned: Boolean) : this(img = img,
                                              file = file,
                                              presentation = presentation,
                                              preferredSize = preferredSize,
                                              isPinned = isFilePinned,
                                              isNorthPanelAvailable = isNorthPanelVisible(UISettings.getInstance()))

    override fun getKey(): VirtualFile = file

    override fun getPreviewImage(): Image? = img

    override fun getPreferredSize(): Dimension = preferredSize

    override fun getDockContainerType(): String = DockableEditorContainerFactory.TYPE

    override fun getPresentation(): Presentation = presentation

    override fun close() {}
  }

  private inner class MyTransferHandler : TransferHandler() {
    private val fileDropHandler = FileDropHandler(null)
    override fun importData(comp: JComponent, t: Transferable): Boolean {
      if (fileDropHandler.canHandleDrop(t.transferDataFlavors)) {
        fileDropHandler.handleDrop(t, window.manager.project, window)
        return true
      }
      return false
    }

    override fun canImport(comp: JComponent, transferFlavors: Array<DataFlavor>): Boolean = fileDropHandler.canHandleDrop(transferFlavors)
  }
}

private class EditorTabs(
  coroutineScope: CoroutineScope,
  parentDisposable: Disposable,
  private val window: EditorWindow,
) : SingleHeightTabs(window.manager.project, parentDisposable), ComponentWithMnemonics, EditorWindowHolder {
  private val _entryPointActionGroup: DefaultActionGroup
  private var isActive = false

  init {
    val listener = AWTEventListener { updateActive() }
    Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.FOCUS_EVENT_MASK)
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
    }
    setUiDecorator(object : UiDecorator {
      override fun getDecoration(): UiDecoration {
        return UiDecoration(
          labelInsets = getTabLabelInsets(),
          contentInsetsSupplier = Function { pos ->
            val actionsOnTheRight: Boolean? = when (pos) {
              ActionsPosition.RIGHT -> true
              ActionsPosition.LEFT -> false
              ActionsPosition.NONE -> null
            }
            JBUI.CurrentTheme.EditorTabs.tabContentInsets(actionsOnTheRight)
          },
          iconTextGap = JBUI.scale(4)
        )
      }
    })
    val source = ActionManager.getInstance().getAction("EditorTabsEntryPoint")
    source.templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
    _entryPointActionGroup = DefaultActionGroup(source)
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
      UISettings.getInstance().hideTabsIfNeeded -> ScrollableMultiRowLayout(this, showPinnedTabsSeparately = true,
                                                                            ExperimentalUI.isEditorTabsWithScrollBar)
      else -> CompressibleMultiRowLayout(this, TabLayout.showPinnedTabsSeparately())
    }
  }

  override fun paintChildren(g: Graphics) {
    super.paintChildren(g)
    drawBorder(g)
  }

  override fun shouldPaintBottomBorder(): Boolean {
    val info = selectedInfo ?: return true
    return !(info.component as EditorWindowTopComponent).composite.selfBorder()
  }

  // return same instance to avoid unnecessary action toolbar updates
  override val entryPointActionGroup: DefaultActionGroup
    get() = _entryPointActionGroup

  private fun getTabLabelInsets(): JBInsets {
    val insets = if (isHorizontalTabs) JBUI.CurrentTheme.EditorTabs.tabInsets() else JBUI.CurrentTheme.EditorTabs.verticalTabInsets()
    return insets as? JBInsets ?: error("JBInsets expected, but was: $insets")
  }

  override fun createTabLabel(info: TabInfo): TabLabel {
    return EditorTabLabel(info)
  }

  private inner class EditorTabLabel(info: TabInfo) : SingleHeightLabel(this, info) {
    init {
      updateFont()
    }

    override fun processMouseEvent(e: MouseEvent) {
      MouseEventAdapter.redispatch(e, scrollBar)
      super.processMouseEvent(e)
    }

    override fun processMouseMotionEvent(e: MouseEvent) {
      MouseEventAdapter.redispatch(e, scrollBar)
      super.processMouseMotionEvent(e)
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

    override fun getPreferredHeight(): Int {
      val insets = getTabLabelInsets().unscaled
      val height = JBUI.scale(UNSCALED_PREF_HEIGHT - insets.top - insets.bottom)
      val layoutInsets = layoutInsets
      return height - layoutInsets.top - layoutInsets.bottom
    }

    override fun isShowTabActions(): Boolean = UISettings.getInstance().showCloseButton || isPinned

    override fun isTabActionsOnTheRight(): Boolean = UISettings.getInstance().closeTabButtonOnTheRight

    override fun shouldPaintFadeout(): Boolean {
      return super.shouldPaintFadeout() && Registry.`is`("ide.editor.tabs.show.fadeout", true)
    }

    override fun editLabelForeground(baseForeground: Color?): Color? {
      return if (baseForeground != null && paintDimmed()) {
        val blendValue = JBUI.CurrentTheme.EditorTabs.unselectedBlend()
        ColorUtil.blendColorsInRgb(effectiveBackground, baseForeground, blendValue.toDouble())
      }
      else baseForeground
    }

    override fun editIcon(baseIcon: Icon): Icon {
      return if (paintDimmed()) {
        IconLoader.getTransparentIcon(baseIcon, JBUI.CurrentTheme.EditorTabs.unselectedAlpha())
      }
      else baseIcon
    }

    private fun paintDimmed(): Boolean {
      return ExperimentalUI.isNewUI() && myTabs.selectedInfo != info && !myTabs.isHoveredTab(this)
    }
  }

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

  override fun getToSelectOnRemoveOf(info: TabInfo): TabInfo? {
    if (window.isDisposed) {
      return null
    }

    val index = getIndexOf(info)
    if (index != -1) {
      val file = window.getFileAt(index)
      val indexToSelect = window.computeIndexToSelect(file, index)
      if (indexToSelect >= 0 && indexToSelect < tabs.size) {
        return getTabAt(indexToSelect)
      }
    }
    return super.getToSelectOnRemoveOf(info)
  }

  override fun revalidateAndRepaint(layoutNow: Boolean) {
    // called from super constructor
    @Suppress("SENSELESS_COMPARISON")
    if (window != null && window.owner.isInsideChange) {
      return
    }
    super.revalidateAndRepaint(layoutNow)
  }
}