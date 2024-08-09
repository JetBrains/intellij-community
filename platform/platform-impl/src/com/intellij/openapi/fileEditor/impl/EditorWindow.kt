// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "PrivatePropertyName")

package com.intellij.openapi.fileEditor.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector
import com.intellij.featureStatistics.fusCollectors.FileEditorCollector.EmptyStateCause
import com.intellij.icons.AllIcons
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.UISettings
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ReadmeShownUsageCollector.README_OPENED_ON_START_TS
import com.intellij.openapi.project.ReadmeShownUsageCollector.logReadmeClosedIn
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.KeyWithDefaultValue
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ComponentUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.TabsUtil
import com.intellij.ui.tabs.impl.JBTabsImpl
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*
import java.awt.event.*
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.time.Instant
import java.util.function.Function
import javax.swing.*
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.roundToInt

private val LOG = logger<EditorWindow>()

class EditorWindow internal constructor(
  val owner: EditorsSplitters,
  @JvmField internal val coroutineScope: CoroutineScope,
) {
  companion object {
    @JvmField
    val DATA_KEY: DataKey<EditorWindow> = DataKey.create("editorWindow")

    @JvmField
    val HIDE_TABS: Key<Boolean> = Key.create("HIDE_TABS")

    // Metadata to support editor tab drag&drop process: initial index
    internal val DRAG_START_INDEX_KEY: Key<Int> = KeyWithDefaultValue.create("drag start editor index", -1)

    // Metadata to support an editor tab drag&drop process: hash of source container
    internal val DRAG_START_LOCATION_HASH_KEY: Key<Int> = KeyWithDefaultValue.create("drag start editor location hash", 0)

    // Metadata to support editor tab drag&drop process: initial 'pinned' state
    internal val DRAG_START_PINNED_KEY: Key<Boolean> = Key.create("drag start editor pinned state")

    @JvmStatic
    val tabLimit: Int
      get() {
        if (DistractionFreeModeController.isDistractionFreeModeEnabled()
            && DistractionFreeModeController.getStandardTabPlacement() == UISettings.TABS_NONE) {
          return 1
        }
        else {
          return UISettings.getInstance().editorTabLimit
        }
      }
  }

  internal val component: JComponent
    get() = tabbedPane.component

  val tabbedPane: EditorTabbedContainer = EditorTabbedContainer(window = this, coroutineScope = coroutineScope)

  val isDisposed: Boolean
    get() = !coroutineScope.isActive

  private val removedTabs = ArrayDeque<Pair<String, FileEditorOpenOptions>>()

  val isShowing: Boolean
    get() = component.isShowing

  @get:Internal
  val manager: FileEditorManagerImpl
    get() = owner.manager

  val tabCount: Int
    get() = tabbedPane.tabCount

  fun setForegroundAt(index: Int, color: Color) {
    tabbedPane.tabs.getTabAt(index).setDefaultForeground(color)
  }

  fun setTextAttributes(index: Int, attributes: TextAttributes?) {
    tabbedPane.tabs.getTabAt(index).setDefaultAttributes(attributes)
  }

  fun setAsCurrentWindow(requestFocus: Boolean) {
    owner.setCurrentWindow(window = this, requestFocus = requestFocus)
  }

  val isEmptyVisible: Boolean
    get() = tabbedPane.editorTabs.isEmptyVisible
  val size: Dimension
    get() = component.size

  private fun checkConsistency() {
    LOG.assertTrue(isValid, "EditorWindow not in collection")
  }

  internal val isValid: Boolean
    get() = owner.containsWindow(this)

  @Suppress("DEPRECATION")
  @get:Deprecated("Use selectedComposite", ReplaceWith("selectedComposite"), level = DeprecationLevel.ERROR)
  val selectedEditor: EditorWithProviderComposite?
    get() = selectedComposite as EditorWithProviderComposite?

  val selectedComposite: EditorComposite?
    get() = currentCompositeFlow.value

  @Suppress("DEPRECATION")
  @Deprecated("Use getSelectedComposite", ReplaceWith("getSelectedComposite(ignorePopup)"), level = DeprecationLevel.ERROR)
  fun getSelectedEditor(@Suppress("UNUSED_PARAMETER") ignorePopup: Boolean): EditorWithProviderComposite? {
    return selectedComposite as EditorWithProviderComposite?
  }

  // used externally
  /**
   * @param ignorePopup if `false` and context a menu is shown currently for some tab,
   * composite for which a menu is invoked will be returned
   */
  @Suppress("MemberVisibilityCanBePrivate", "unused")
  fun getSelectedComposite(ignorePopup: Boolean): EditorComposite? {
    return (if (ignorePopup) tabbedPane.editorTabs.selectedInfo else tabbedPane.editorTabs.targetInfo)?.composite
  }

  /**
   * A file in a context.
   * For example, if a context menu is shown currently for some tab, the composite for which a menu is invoked will be returned
   */
  @Internal
  fun getContextFile(): VirtualFile? = tabbedPane.tabs.targetInfo?.composite?.file

  val allComposites: List<EditorComposite>
    get() = composites().toList()

  fun composites(): Sequence<EditorComposite> = tabbedPane.tabs.tabs.asSequence().map { it.composite }

  @Suppress("DEPRECATION")
  @get:Deprecated("{@link #getAllComposites()}", ReplaceWith("allComposites)"), level = DeprecationLevel.ERROR)
  val editors: Array<EditorWithProviderComposite>
    get() = composites().filterIsInstance<EditorWithProviderComposite>().toList().toTypedArray()

  @Deprecated("Use [fileList]", replaceWith = ReplaceWith("fileList"), level = DeprecationLevel.ERROR)
  val files: Array<VirtualFile>
    get() = files().toList().toTypedArray()

  val fileList: List<VirtualFile>
    get() = files().toList()

  @RequiresEdt
  internal fun files(): Sequence<VirtualFile> = composites().map { it.file }

  private val _currentCompositeFlow: MutableStateFlow<EditorComposite?> = MutableStateFlow(null)

  @JvmField
  internal val currentCompositeFlow: StateFlow<EditorComposite?> = _currentCompositeFlow.asStateFlow()

  init {
    updateTabsVisibility()

    tabbedPane.tabs.addListener(object : TabsListener {
      override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
        if (newSelection != null) {
          val newFile = newSelection.`object` as VirtualFile
          if (GeneralSettings.getInstance().isSyncOnFrameActivation) {
            VfsUtil.markDirtyAndRefresh(true, false, false, newFile)
          }
        }

        _currentCompositeFlow.value = newSelection?.composite
      }
    })
  }

  internal enum class RelativePosition(@JvmField val swingConstant: Int) {
    CENTER(SwingConstants.CENTER),
    UP(SwingConstants.TOP),
    LEFT(SwingConstants.LEFT),
    DOWN(SwingConstants.BOTTOM),
    RIGHT(SwingConstants.RIGHT)
  }

  internal fun getAdjacentEditors(): Map<RelativePosition, EditorWindow> {
    checkConsistency()
    // can't have more than 4
    val adjacentEditors = HashMap<RelativePosition, EditorWindow>(4)
    val windows = owner.getOrderedWindows()
    windows.remove(this)

    val panelToWindow = HashMap<JComponent, EditorWindow>()
    for (window in windows) {
      panelToWindow.put(window.component, window)
    }

    val relativePoint = if (ApplicationManager.getApplication().isUnitTestMode) {
      RelativePoint(MouseEvent(JLabel(), 0, 0, 0, 0, 0, 0, false))
    }
    else {
      RelativePoint(component.locationOnScreen)
    }
    val point = relativePoint.getPoint(owner)
    val nearestComponent: (Int, Int) -> Component? = { x, y ->
      SwingUtilities.getDeepestComponentAt(owner, x, y)
    }

    fun findAdjacentEditor(startComponent: Component?): EditorWindow? {
      var component = startComponent
      while (component !== owner && component != null) {
        panelToWindow.get(component)?.let {
          return it
        }
        component = component.parent ?: break
      }
      return null
    }

    fun biConsumer(window: EditorWindow?, position: RelativePosition) {
      if (window != null) {
        adjacentEditors.put(position, window)
      }
    }

    // Even if the above/below adjacent editor is shifted a bit to the right from the left edge of the current editor,
    // still try to choose an editor that is visually above/below - shifted nor more than a quarter of editor width.
    val x = point.x + component.width / 4
    // Splitter has width of one pixel - we need to step at least 2 pixels to be over an adjacent editor
    val searchStep = 2
    biConsumer(findAdjacentEditor(nearestComponent(x, point.y - searchStep)), RelativePosition.UP)
    biConsumer(findAdjacentEditor(nearestComponent(x, point.y + component.height + searchStep)), RelativePosition.DOWN)
    biConsumer(findAdjacentEditor(nearestComponent(point.x - searchStep, point.y)), RelativePosition.LEFT)
    biConsumer(findAdjacentEditor(nearestComponent(point.x + component.width + searchStep, point.y)), RelativePosition.RIGHT)
    return adjacentEditors
  }

  @Deprecated("{@link #setSelectedComposite(EditorComposite, boolean)}",
              ReplaceWith("setSelectedComposite(composite, focusEditor)"),
              level = DeprecationLevel.ERROR)
  fun setSelectedEditor(composite: EditorComposite, focusEditor: Boolean) {
    setSelectedComposite(composite = composite, focusEditor = focusEditor)
  }

  fun setSelectedComposite(file: VirtualFile, focusEditor: Boolean) {
    // select a composite in a tabbed pane and then focus on a composite if needed
    for (tab in tabbedPane.tabs.tabs) {
      if (tab.composite.file == file) {
        tabbedPane.tabs.select(tab, focusEditor)
        break
      }
    }
  }

  fun setSelectedComposite(composite: EditorComposite, focusEditor: Boolean) {
    // select a composite in a tabbed pane and then focus on a composite if needed
    for (tab in tabbedPane.tabs.tabs) {
      if (tab.composite === composite) {
        tabbedPane.tabs.select(tab, focusEditor)
        break
      }
    }

    val currentComposite = currentCompositeFlow.value
    if (currentComposite !== composite) {
      LOG.error("$currentComposite is not equal to $composite")
    }
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use {@link #setComposite(EditorComposite, boolean)}",
              ReplaceWith("setComposite(editor, FileEditorOpenOptions().withRequestFocus(focusEditor))",
                          "com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions"))
  fun setEditor(@Suppress("DEPRECATION") editor: EditorWithProviderComposite, focusEditor: Boolean) {
    addComposite(
      composite = editor,
      file = editor.file,
      options = FileEditorOpenOptions(requestFocus = focusEditor),
      isNewEditor = findTabByComposite(composite = editor) == null,
    )
  }

  @RequiresEdt
  internal fun addComposite(
    composite: EditorComposite,
    file: VirtualFile,
    options: FileEditorOpenOptions,
    isNewEditor: Boolean,
  ) {
    val isPreviewMode = (isNewEditor || composite.isPreview) && shouldReservePreview(composite.file, options, owner.manager.project)
    composite.isPreview = isPreviewMode
    if (isNewEditor) {
      owner.scheduleUpdateFileIcon(file)

      var indexToInsert = options.index
      if (indexToInsert == -1) {
        if (isPreviewMode) {
          indexToInsert = composites().indexOfLast { it.isPreview }
        }
        if (indexToInsert == -1) {
          indexToInsert = if (UISettings.getInstance().openTabsAtTheEnd) tabbedPane.tabCount else tabbedPane.selectedIndex + 1
        }
      }

      val template = AllIcons.FileTypes.Text
      val tab = tabbedPane.insertTab(
        file = file,
        icon = EmptyIcon.create(template.iconWidth, template.iconHeight),
        component = composite.component,
        tooltip = null,
        indexToInsert = indexToInsert,
        selectedEditor = composite.selectedEditor,
        parentDisposable = composite,
      )

      watchForTabActions(composite = composite, tab = tab)

      var dragStartIndex: Int? = null
      val hash = file.getUserData(DRAG_START_LOCATION_HASH_KEY)
      if (hash != null && System.identityHashCode(tabbedPane.tabs) == hash) {
        dragStartIndex = file.getUserData(DRAG_START_INDEX_KEY)
      }
      if (dragStartIndex == null || dragStartIndex != -1) {
        val initialPinned = file.getUserData(DRAG_START_PINNED_KEY)
        if (initialPinned != null) {
          composite.isPinned = initialPinned
        }
        else if (composite.isPinned) {
          composite.isPinned = true
        }
      }
      file.putUserData(DRAG_START_LOCATION_HASH_KEY, null)
      file.putUserData(DRAG_START_INDEX_KEY, null)
      file.putUserData(DRAG_START_PINNED_KEY, null)

      trimToSize(fileToIgnore = file, transferFocus = false)
    }

    owner.scheduleUpdateFileColor(file)

    if (options.pin) {
      setFilePinned(composite = composite, pinned = true)
    }

    if (options.selectAsCurrent) {
      setCurrentCompositeAndSelectTab(composite)

      owner.setCurrentWindow(window = this@EditorWindow)
      // If you invoke action via context menu, then on mouse release we will process focus event,
      // and EditorSplitters.MyFocusWatcher will focus the old editor window.
      // So, we must use doWhenFocusSettlesDown.
      val isHeadless = ApplicationManager.getApplication().isHeadlessEnvironment
      if (isHeadless) {
        owner.setCurrentWindow(window = this@EditorWindow)
      }

      composite.coroutineScope.launch(Dispatchers.EDT + ClientId.coroutineContext() + ModalityState.any().asContextElement()) {
        if (!isHeadless) {
          owner.setCurrentWindow(window = this@EditorWindow)
        }

        // transfer focus into editor
        if (options.requestFocus) {
          withContext(Dispatchers.Default) {
            composite.waitForAvailable()
          }
          focusEditorOnComposite(composite = composite, splitters = owner)
        }
      }
    }

    updateTabsVisibility()
    owner.validate()
  }

  internal fun watchForTabActions(composite: EditorComposite, tab: TabInfo) {
    // on unsplit, we transfer composite to another window, so, we launch in the window's scope
    coroutineScope.launch {
      attachAsChildTo(composite.coroutineScope)
      composite.selectedEditorWithProvider.collectLatest {
        val tabActions = it?.fileEditor?.tabActions
        withContext(Dispatchers.EDT) {
          if (tab.tabPaneActions != tabActions) {
            tab.setTabPaneActions(tabActions)
            tabbedPane.editorTabs.updateEntryPointToolbar(tabActionGroup = tabActions)
          }
        }
      }
    }
  }

  // we must select tab in the same EDT event (same command) - IdeDocumentHistoryImpl rely on that
  @RequiresEdt
  internal fun setCurrentCompositeAndSelectTab(composite: EditorComposite) {
    tabbedPane.tabs.tabs.find { it.composite == composite }?.let {
      tabbedPane.editorTabs.select(info = it, requestFocus = false)
    }
    _currentCompositeFlow.value = composite
  }

  // we must select tab in the same EDT event (same command) - IdeDocumentHistoryImpl rely on that
  @RequiresEdt
  internal fun setCurrentCompositeAndSelectTab(tab: TabInfo) {
    tabbedPane.editorTabs.select(info = tab, requestFocus = false)
    _currentCompositeFlow.value = tab.composite
  }

  @RequiresEdt
  internal fun selectTabOnStartup(tab: TabInfo, requestFocus: Boolean, windowAdded: suspend () -> Unit) {
    val composite = tab.composite
    tabbedPane.editorTabs.selectTabSilently(tab)
    _currentCompositeFlow.value = composite
    owner.setCurrentWindow(window = this)

    if (requestFocus) {
      composite.coroutineScope.launch {
        // well, we cannot focus if component is not added
        windowAdded()
        // wait for the file editor
        composite.waitForAvailable()
        if (withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            focusEditorOnComposite(composite = composite, splitters = owner, toFront = false)
          }) {
          // update frame title only when the first file editor is ready to load (editor is not yet fully loaded at this moment)
          owner.updateFrameTitle()
        }
      }
    }
  }

  @JvmOverloads
  @RequiresEdt
  fun split(
    orientation: Int,
    forceSplit: Boolean,
    virtualFile: VirtualFile?,
    focusNew: Boolean,
    fileIsSecondaryComponent: Boolean = true,
  ): EditorWindow? {
    checkConsistency()
    if (tabCount < 1) {
      return null
    }

    if (!forceSplit && inSplitter()) {
      val target = siblings().first()
      val selectedComposite = selectedComposite
      if (virtualFile != null && selectedComposite != null) {
        owner.manager.openFileImpl(
          window = target,
          _file = virtualFile,
          entry = selectedComposite.takeIf { it.file == virtualFile }?.currentStateAsFileEntry(),
          options = FileEditorOpenOptions(requestFocus = focusNew),
        )
      }
      return target
    }

    if (tabCount == 0) {
      return null
    }

    val splitter = createSplitter(isVertical = orientation == JSplitPane.VERTICAL_SPLIT, proportion = 0.5f, minProp = 0.1f, maxProp = 0.9f)
    splitter.putClientProperty(EditorsSplitters.SPLITTER_KEY, true)

    val selectedComposite = selectedComposite
    val existingEditor = tabbedPane.component

    val newWindow = EditorWindow(owner = owner, coroutineScope = owner.coroutineScope.childScope("EditorWindow"))
    owner.addWindow(newWindow)

    swapComponents(parent = existingEditor.parent as JPanel, toAdd = splitter, toRemove = existingEditor)
    if (fileIsSecondaryComponent) {
      splitter.firstComponent = existingEditor
      splitter.secondComponent = newWindow.component
    }
    else {
      splitter.firstComponent = newWindow.component
      splitter.secondComponent = existingEditor
    }
    normalizeProportionsIfNeed(existingEditor)

    // open only selected file in the new splitter instead of opening all tabs
    val nextFile = virtualFile ?: selectedComposite!!.file
    val composite = owner.manager.openFileInNewCompositeInEdt(
      window = newWindow,
      file = nextFile,
      fileEntry = selectedComposite?.takeIf { it.file == nextFile }?.currentStateAsFileEntry(),
      options = FileEditorOpenOptions(
        requestFocus = focusNew,
        isExactState = true,
        pin = getComposite(nextFile)?.isPinned ?: false,
        selectAsCurrent = focusNew,
      ),
    ) ?: return newWindow
    if (!focusNew) {
      LOG.assertTrue(currentCompositeFlow.value == selectedComposite)
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
        selectedComposite?.preferredFocusedComponent?.let {
          IdeFocusManager.getGlobalInstance().requestFocus(it, true)
        }
      }

      // we set selectAsCurrent to false, but the newly created window should have some selected composite
      if (composite is EditorComposite) {
        newWindow.setCurrentCompositeAndSelectTab(composite)
      }
    }
    component.revalidate()
    return newWindow
  }

  private fun normalizeProportionsIfNeed(inputComponent: Container) {
    var component = inputComponent
    if (!AdvancedSettings.getBoolean("editor.normalize.splits")) {
      return
    }

    var isVertical = when {
      component is Splitter -> component.isVertical
      component.components.firstOrNull() is Splitter -> (component.components.first() as Splitter).isVertical
      else -> null
    }

    val hierarchyStack = LinkedHashMap<Splitter, Boolean>()
    while (component !== manager.component) {
      val parent = component.parent
      if (parent is Splitter) {
        if (isVertical === null) {
          // stack orientation (row or column) is not yet defined
          isVertical = parent.isVertical
        }
        else if (isVertical != parent.isVertical) {
          break
        }
        hierarchyStack.put(parent, parent.firstComponent === component)
      }
      component = parent ?: break
    }

    var i = 0
    for ((key, value) in hierarchyStack) {
      key.proportion = if (value) 1 - 1f / (2 + i) else 1f / (2 + i)
      i++
    }
  }

  @Deprecated("Use getSiblings()", ReplaceWith("getSiblings()"))
  fun findSiblings(): Array<EditorWindow> = siblings().toList().toTypedArray()

  internal fun getSiblings(): List<EditorWindow> = siblings().toList()

  @RequiresEdt
  internal fun siblings(): Sequence<EditorWindow> {
    checkConsistency()
    val splitter = (component.parent as? Splitter) ?: return emptySequence()
    return owner.windows().filter { it != this@EditorWindow && SwingUtilities.isDescendingFrom(it.component, splitter) }
  }

  fun requestFocus(forced: Boolean) {
    IdeFocusManager.getGlobalInstance().requestFocus(tabbedPane.editorTabs.component, forced)
  }

  fun toFront() {
    UIUtil.toFront(ComponentUtil.getWindow(tabbedPane.component))
  }

  internal fun updateTabsVisibility(uiSettings: UISettings = UISettings.getInstance()) {
    tabbedPane.editorTabs.isHideTabs = (owner.isFloating && tabCount == 1 && (owner.isSingletonEditorInWindow || shouldHideTabs(selectedComposite))) ||
                                       uiSettings.editorTabPlacement == UISettings.TABS_NONE ||
                                       (uiSettings.presentationMode && !Registry.`is`("ide.editor.tabs.visible.in.presentation.mode"))
  }

  fun closeAllExcept(selectedFile: VirtualFile?) {
    runBulkTabChange(owner) {
      for (file in files().toList()) {
        if (file != selectedFile && !isFilePinned(file)) {
          closeFile(file)
        }
      }
    }
  }

  internal fun dispose() {
    coroutineScope.cancel()
    owner.removeWindow(this)
  }

  internal fun hasClosedTabs(): Boolean = !removedTabs.isEmpty()

  @RequiresEdt
  internal fun restoreClosedTab() {
    val info = removedTabs.removeLastOrNull() ?: return
    val file = VirtualFileManager.getInstance().findFileByUrl(info.first) ?: return
    manager.openFileImpl(
      window = this,
      _file = file,
      entry = null,
      options = info.second.copy(selectAsCurrent = true, requestFocus = true, waitForCompositeOpen = false),
    )
  }

  fun closeFile(file: VirtualFile, disposeIfNeeded: Boolean = true, @Suppress("UNUSED_PARAMETER") transferFocus: Boolean = true) {
    closeFile(file = file, disposeIfNeeded = disposeIfNeeded)
  }

  @JvmOverloads
  fun closeFile(file: VirtualFile, disposeIfNeeded: Boolean = true) {
    val composite = getComposite(file) ?: return
    closeFile(file = file, composite = composite, disposeIfNeeded = disposeIfNeeded)
  }

  @RequiresEdt
  internal fun closeFile(file: VirtualFile, composite: EditorComposite, disposeIfNeeded: Boolean = true) {
    runBulkTabChange(owner) {
      val fileEditorManager = manager
      try {
        WriteIntentReadAction.run {
          fileEditorManager.project.messageBus.syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER)
            .beforeFileClosed(fileEditorManager, file)
        }
        val componentIndex = findComponentIndex(composite)
        val editorTabs = tabbedPane.editorTabs
        // composite could close itself on decomposition
        if (componentIndex >= 0) {
          removedTabs.addLast(file.url to FileEditorOpenOptions(index = componentIndex, pin = composite.isPinned))
          if (removedTabs.size >= tabLimit) {
            removedTabs.removeFirst()
          }

          val info = editorTabs.getTabAt(componentIndex)
          if (isDisposed || !manager.project.isOpen) {
            editorTabs.removeTabWithoutChangingSelection(info)
          }
          else {
            val toSelect = getTabToSelect(tabBeingClosed = info, fileBeingClosed = file, componentIndex = componentIndex)
            editorTabs.removeTab(info = info, forcedSelectionTransfer = toSelect)
          }
          fileEditorManager.disposeComposite(composite)
        }

        if (disposeIfNeeded && tabCount == 0) {
          removeFromSplitter()
          logEmptyStateIfMainSplitter(cause = EmptyStateCause.ALL_TABS_CLOSED)
        }
        else {
          component.revalidate()
        }

        if (editorTabs.selectedInfo == null) {
          // selection event is not fired
          _currentCompositeFlow.value = null
        }
      }
      finally {
        val openedTs = file.getUserData(README_OPENED_ON_START_TS)
        if (openedTs != null) {
          file.putUserData(README_OPENED_ON_START_TS, null)
          val wasOpenedMillis = Instant.now().toEpochMilli() - openedTs.toEpochMilli()
          logReadmeClosedIn(wasOpenedMillis)
        }

        fileEditorManager.removeSelectionRecord(file = file, window = this)
        val project = fileEditorManager.project
        if (!project.isDisposed) {
          project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(fileEditorManager, file)
        }
        owner.afterFileClosed(file)
      }
    }
  }

  internal fun getTabToSelect(
    tabBeingClosed: TabInfo,
    fileBeingClosed: VirtualFile,
    componentIndex: Int,
  ): TabInfo? {
    tabBeingClosed.previousSelection?.let {
      return it
    }

    val indexToSelect = computeIndexToSelect(fileBeingClosed = fileBeingClosed, fileIndex = componentIndex)
    val editorTabs = tabbedPane.editorTabs
    return if (indexToSelect >= 0 && indexToSelect < editorTabs.tabCount) editorTabs.getTabAt(indexToSelect) else null
  }

  internal fun logEmptyStateIfMainSplitter(cause: EmptyStateCause) {
    require(tabCount == 0) { "Tab count expected to be zero" }
    if (EditorEmptyTextPainter.isEnabled() && component.parent === manager.mainSplitters) {
      FileEditorCollector.logEditorEmptyState(manager.project, cause)
    }
  }

  fun removeFromSplitter() {
    if (!inSplitter()) {
      return
    }

    if (owner.currentWindow.let { it == this || it == null }) {
      owner.setCurrentWindow(window = siblings().firstOrNull(), requestFocus = true)
    }

    val splitter = component.parent as Splitter
    val otherComponent = splitter.getOtherComponent(component)

    when (val parent = splitter.parent) {
      is Splitter -> {
        if (parent.firstComponent === splitter) {
          parent.firstComponent = otherComponent
        }
        else {
          parent.secondComponent = otherComponent
        }
        normalizeProportionsIfNeed(owner.currentWindow!!.component)
      }
      is EditorsSplitters -> {
        val currentFocusComponent = IdeFocusManager.getGlobalInstance().getFocusedDescendantFor(parent)
        parent.removeAll()
        parent.add(otherComponent, BorderLayout.CENTER)
        parent.revalidate()
        currentFocusComponent?.requestFocusInWindow()
      }
      else -> LOG.error("Unknown container: $parent")
    }
    dispose()
  }

  internal fun computeIndexToSelect(fileBeingClosed: VirtualFile, fileIndex: Int): Int {
    val currentlySelectedIndex = tabbedPane.selectedIndex
    if (currentlySelectedIndex != fileIndex) {
      // if the file being closed is not currently selected, keep the currently selected file open
      return currentlySelectedIndex
    }

    val uiSettings = UISettings.getInstance()
    if (uiSettings.state.activeMruEditorOnClose) {
      // try to open last visited file
      val histFiles = EditorHistoryManager.getInstance(manager.project).fileList
      for (index in histFiles.indices.reversed()) {
        val histFile = histFiles.get(index)
        if (histFile == fileBeingClosed) {
          continue
        }

        val histFileIndex = findComponentIndex(getComposite(histFile) ?: continue)
        if (histFileIndex >= 0) {
          // if the file being closed is located before the hist file, then after closing, the index of the histFile will be shifted by -1
          return histFileIndex
        }
      }
    }
    else if (uiSettings.activeRightEditorOnClose && fileIndex + 1 < tabbedPane.tabCount) {
      return fileIndex + 1
    }

    // by default, select the previous neighbor
    return if (fileIndex > 0) fileIndex - 1 else -1
  }

  internal interface SplitChooser {
    val position: RelativePosition

    fun positionChanged(position: RelativePosition)

    fun dispose()
  }

  internal fun showSplitChooser(project: Project, showInfoPanel: Boolean): SplitChooser {
    val disposable = Disposer.newDisposable("GlassPaneListeners")
    val painter = MySplitPainter(project, showInfoPanel, tabbedPane, owner)
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      IdeGlassPaneUtil.find(component).addPainter(component, painter, disposable)
    }

    // editor size can change when we increase tool window size using mouse or due to some other reasons
    // we need to adapt the painter size accordingly
    val updatePainterSizeOnTabbedPaneResizeListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        painter.updateRectangleAndRepaint()
      }
    }
    component.addComponentListener(updatePainterSizeOnTabbedPaneResizeListener)

    //Reminder about UI components hierarchy:
    //    EditorsSplitters
    //    |-- EditorTabs                    // `tabbedPane.component` OR `component`
    //        |-- EditorWindowTopComponent  //
    //            |-- EditorCompositePanel  // `tabbedPane.getSelectedComposite()`
    //                |-- JPanel            // `componentToFocus`
    //                    |-- PsiAwareTextEditorComponent
    //
    //assuming that it's safe to `!!`, if `showSplitChooser` is called, we expect that selected editor exists and it's not null
    val componentToFocus = tabbedPane.tabs.selectedInfo!!.component.getComponent(0) as JComponent

    componentToFocus.repaint()
    componentToFocus.isFocusable = true
    componentToFocus.grabFocus()
    componentToFocus.focusTraversalKeysEnabled = false

    val focusAdapter = object : FocusAdapter() {
      override fun focusLost(e: FocusEvent) {
        component.removeComponentListener(updatePainterSizeOnTabbedPaneResizeListener)

        componentToFocus.removeFocusListener(this)
        val splitterService = SplitterService.getInstance(project)
        if (splitterService.activeWindow == this@EditorWindow) {
          splitterService.stopSplitChooser(true)
        }
      }
    }
    componentToFocus.addFocusListener(focusAdapter)

    return object : SplitChooser {
      override val position: RelativePosition
        get() = painter.position

      override fun positionChanged(position: RelativePosition) {
        painter.positionChanged(position)
      }

      override fun dispose() {
        component.removeComponentListener(updatePainterSizeOnTabbedPaneResizeListener)

        painter.rectangle = null
        componentToFocus.removeFocusListener(focusAdapter)
        componentToFocus.isFocusable = false
        componentToFocus.repaint()
        Disposer.dispose(disposable)
      }
    }
  }

  fun changeOrientation() {
    checkConsistency()
    val parent = component.parent
    if (parent is Splitter) {
      parent.orientation = !parent.orientation
    }
  }

  internal fun unsplit(setCurrent: Boolean) {
    checkConsistency()
    val splitter = component.parent as? Splitter ?: return
    val siblingWindows = owner.windows().filter { it != this@EditorWindow && SwingUtilities.isDescendingFrom(it.component, splitter) }.toList()

    // selected editors will be added first
    var compositeToSelect = selectedComposite ?: siblingWindows.firstNotNullOfOrNull { eachSibling -> eachSibling.selectedComposite }

    // we'll select and focus on a single editor in the end
    val openOptions = FileEditorOpenOptions(selectAsCurrent = false, requestFocus = false)
    val editorTabLimit = UISettings.getInstance().state.editorTabLimit
    for (siblingWindow in siblingWindows) {
      for (siblingComposite in siblingWindow.composites().toList()) {
        if (compositeToSelect == null) {
          compositeToSelect = siblingComposite
        }
        if (tabbedPane.tabs.tabs.firstOrNull { it.composite.file == siblingComposite.file } == null && tabCount < editorTabLimit) {
          addComposite(
            composite = siblingComposite,
            file = siblingComposite.file,
            options = openOptions,
            isNewEditor = true,
          )
        }
        else {
          manager.disposeComposite(siblingComposite)
        }
      }
      siblingWindow.dispose()
    }

    val parent = splitter.parent as JPanel
    swapComponents(parent = parent, toAdd = tabbedPane.component, toRemove = splitter)
    parent.revalidate()

    if (compositeToSelect != null) {
      setSelectedComposite(composite = compositeToSelect, focusEditor = true)
    }
    if (setCurrent) {
      owner.setCurrentWindow(window = this, requestFocus = false)
    }
    normalizeProportionsIfNeed(component)
  }

  fun unsplitAll() {
    checkConsistency()
    while (inSplitter()) {
      unsplit(setCurrent = true)
    }
  }

  fun inSplitter(): Boolean {
    checkConsistency()
    return component.parent is Splitter
  }

  val selectedFile: VirtualFile?
    get() {
      checkConsistency()
      return selectedComposite?.file
    }

  @Suppress("DEPRECATION")
  @Deprecated("Use {@link #getComposite(VirtualFile)}", ReplaceWith("getComposite(file)"))
  fun findFileComposite(file: VirtualFile): EditorWithProviderComposite? = getComposite(file) as EditorWithProviderComposite?

  fun getComposite(inputFile: VirtualFile): EditorComposite? = findTabByFile(inputFile)?.composite

  internal fun findCompositeAndTab(inputFile: VirtualFile): Pair<EditorComposite, TabInfo>? {
    val file = (inputFile as? BackedVirtualFile)?.originFile ?: inputFile
    for (tab in tabbedPane.tabs.tabs) {
      val composite = tab.composite
      if (composite.file == file) {
        return composite to tab
      }
    }
    return null
  }

  private fun findComponentIndex(composite: EditorComposite): Int = tabbedPane.tabs.tabs.indexOfFirst { it.component === composite.component }

  internal fun findTabByComposite(composite: EditorComposite): TabInfo? = tabbedPane.tabs.tabs.firstOrNull { it.composite === composite }

  internal fun findTabByFile(file: VirtualFile): TabInfo? = tabbedPane.tabs.tabs.firstOrNull { it.composite.file == file }

  fun isFileOpen(file: VirtualFile): Boolean = getComposite(file) != null

  fun isFilePinned(file: VirtualFile): Boolean {
    return requireNotNull(getComposite(file)) { "file is not open: $file" }.isPinned
  }

  fun setFilePinned(file: VirtualFile, pinned: Boolean) {
    setFilePinned(composite = requireNotNull(getComposite(file)) { "file is not open: $file" }, pinned = pinned)
  }

  private fun setFilePinned(composite: EditorComposite, pinned: Boolean) {
    val wasPinned = composite.isPinned
    composite.isPinned = pinned
    if (pinned && composite.isPreview) {
      composite.isPreview = false
      owner.scheduleUpdateFileColor(composite.file)
    }
    if (wasPinned != pinned && EDT.isCurrentThreadEdt()) {
      (tabbedPane.tabs as? JBTabsImpl)?.doLayout()
    }
  }

  fun trimToSize(fileToIgnore: VirtualFile?, transferFocus: Boolean) {
    if (!isDisposed) {
      doTrimSize(
        fileToIgnore = fileToIgnore,
        closeNonModifiedFilesFirst = UISettings.getInstance().state.closeNonModifiedFilesFirst,
        transferFocus = transferFocus,
      )
    }
  }

  private fun doTrimSize(fileToIgnore: VirtualFile?, closeNonModifiedFilesFirst: Boolean, transferFocus: Boolean) {
    val selectedFile = selectedFile

    val alreadyClosedFile = if (selectedFile != null && shouldCloseSelected(selectedFile, fileToIgnore)) {
      defaultCloseFile(selectedFile, transferFocus)
      selectedFile
    }
    else {
      null
    }

    // close all preview tabs
    for (file in composites().filter { it.isPreview }.map { it.file }.filter { it != fileToIgnore }.distinct().toList()) {
      defaultCloseFile(file = file, transferFocus = transferFocus)
    }

    val limit = tabLimit
    fun isUnderLimit(): Boolean = tabbedPane.tabCount <= limit || tabbedPane.tabCount == 0 || !isAnyTabClosable(fileToIgnore)

    if (isUnderLimit()) {
      return
    }

    val closingOrder = getTabClosingOrder(closeNonModifiedFilesFirst)
    for (file in closingOrder) {
      if (isUnderLimit()) {
        return
      }

      if (file != alreadyClosedFile && fileCanBeClosed(file, fileToIgnore)) {
        defaultCloseFile(file, transferFocus)
      }
    }
  }

  private fun getTabClosingOrder(closeNonModifiedFilesFirst: Boolean): MutableSet<VirtualFile> {
    val allFiles = files().toList()
    val histFiles = EditorHistoryManager.getInstance(manager.project).fileList
    val closingOrder = LinkedHashSet<VirtualFile>()

    // first, we search for files not in history
    for (file in allFiles) {
      if (!histFiles.contains(file)) {
        closingOrder.add(file)
      }
    }
    if (closeNonModifiedFilesFirst) {
      // Search in history
      for (file in histFiles) {
        val composite = getComposite(file)
        if (composite != null && !owner.manager.isChanged(composite)) {
          // we found a non-modified file
          closingOrder.add(file)
        }
      }

      // Search in tabbed pane
      for (composite in composites()) {
        if (!owner.manager.isChanged(composite)) {
          // we found a non-modified file
          closingOrder.add(composite.file)
        }
      }
    }

    // If it's not enough to close non-modified files only, try all other files.
    // Search in history from less frequently used.
    closingOrder.addAll(histFiles)

    // finally, close tabs by their order
    for (composite in composites()) {
      closingOrder.add(composite.file)
    }
    val selectedFile = selectedFile
    // selected should be closed last
    if (selectedFile != null) {
      closingOrder.remove(selectedFile)
      closingOrder.add(selectedFile)
    }
    return closingOrder
  }

  private fun shouldCloseSelected(file: VirtualFile, fileToIgnore: VirtualFile?): Boolean {
    if (!UISettings.getInstance().reuseNotModifiedTabs || !owner.manager.project.isInitialized) {
      return false
    }

    val composite = getComposite(file) ?: return false
    if (composite.isPinned || file == fileToIgnore) {
      return false
    }

    // don't check focus in unit test mode
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      val owner = IdeFocusManager.getInstance(owner.manager.project).focusOwner
      if (owner == null) {
        return false
      }
      val component = composite.selectedEditor?.component
      if (component == null || !SwingUtilities.isDescendingFrom(owner, component)) {
        return false
      }
    }
    return !owner.manager.isChanged(composite)
  }

  private fun isAnyTabClosable(fileToIgnore: VirtualFile?): Boolean {
    return tabbedPane.tabs.tabs.asReversed().any { fileCanBeClosed(it.composite.file, fileToIgnore) }
  }

  private fun defaultCloseFile(file: VirtualFile, transferFocus: Boolean) {
    closeFile(file = file, disposeIfNeeded = true, transferFocus = transferFocus)
  }

  private fun fileCanBeClosed(file: VirtualFile, fileToIgnore: VirtualFile?): Boolean {
    if (file is BackedVirtualFile && file.originFile == fileToIgnore) {
      return false
    }
    return isFileOpen(file) && file != fileToIgnore && !isFilePinned(file) && isClosingAllowed(file)
  }

  private fun isClosingAllowed(file: VirtualFile): Boolean {
    val extensions = EditorAutoClosingHandler.EP_NAME.extensionList
    if (extensions.isEmpty()) {
      return true
    }

    val composite = getComposite(file) ?: return true
    return extensions.all { it.isClosingAllowed(composite) }
  }

  override fun toString(): String {
    if (EDT.isCurrentThreadEdt()) {
      return "EditorWindow(files=${composites().joinToString { it.file.path }})"
    }
    else {
      return super.toString()
    }
  }
}

private fun shouldReservePreview(file: VirtualFile, options: FileEditorOpenOptions, project: Project): Boolean {
  return when {
    !UISettings.getInstance().openInPreviewTabIfPossible -> false
    FileEditorManagerImpl.FORBID_PREVIEW_TAB.get(file, false) -> false
    options.usePreviewTab -> true
    !options.selectAsCurrent || options.requestFocus -> false
    else -> {
      val focusOwner = IdeFocusManager.getInstance(project).focusOwner ?: return false
      hasClientPropertyInHierarchy(focusOwner, FileEditorManagerImpl.OPEN_IN_PREVIEW_TAB)
    }
  }
}

private fun hasClientPropertyInHierarchy(owner: Component, @Suppress("SameParameterValue") propertyKey: Key<Boolean>): Boolean {
  var component = owner
  while (true) {
    if (component is JComponent && component.getClientProperty(propertyKey) == true) {
      return true
    }

    component = component.parent ?: break
  }
  return false
}

private fun swapComponents(parent: JPanel, toAdd: JComponent, toRemove: JComponent) {
  if (parent is Splitter) {
    if (parent.firstComponent === toRemove) {
      parent.firstComponent = toAdd
    }
    else {
      check(parent.secondComponent === toRemove)
      parent.secondComponent = toAdd
    }
  }
  else {
    check(parent is EditorsSplitters)
    parent.remove(toRemove)
    parent.add(toAdd, BorderLayout.CENTER)
  }
}

private fun shouldHideTabs(composite: EditorComposite?): Boolean {
  return composite != null && composite.allEditors.any { EditorWindow.HIDE_TABS.get(it, false) }
}

private class MySplitPainter(
  private val project: Project,
  private var showInfoPanel: Boolean,
  private val tabbedPane: EditorTabbedContainer,
  private val owner: EditorsSplitters,
) : AbstractPainter() {
  var rectangle: Shape? = tabbedPane.tabs.dropArea
  var position: EditorWindow.RelativePosition = EditorWindow.RelativePosition.CENTER

  override fun needsRepaint(): Boolean = rectangle != null

  override fun executePaint(component: Component, g: Graphics2D) {
    if (rectangle == null) {
      return
    }

    GraphicsUtil.setupAAPainting(g)
    g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
    g.fill(rectangle)
    if (position == EditorWindow.RelativePosition.CENTER && showInfoPanel) {
      drawInfoPanel(component, g)
    }
  }

  private fun drawInfoPanel(component: Component, g: Graphics2D) {
    val rectangle = rectangle!!
    val centerX = rectangle.bounds.x + rectangle.bounds.width / 2
    val centerY = rectangle.bounds.y + rectangle.bounds.height / 2
    val height = Registry.intValue("ide.splitter.chooser.info.panel.height")
    var width = Registry.intValue("ide.splitter.chooser.info.panel.width")
    val arc = Registry.intValue("ide.splitter.chooser.info.panel.arc")
    val openShortcuts = IdeBundle.message(
      "split.with.chooser.move.tab",
      getShortcut("SplitChooser.Split"),
      if (SplitterService.getInstance(project).initialEditorWindow != null) {
        IdeBundle.message("split.with.chooser.duplicate.tab", getShortcut("SplitChooser.Duplicate"))
      }
      else {
        ""
      })
    val switchShortcuts = IdeBundle.message("split.with.chooser.switch.tab", getShortcut("SplitChooser.NextWindow"))

    // Adjust the default width to an info text
    val font = StartupUiUtil.labelFont
    val fontMetrics = g.getFontMetrics(font)
    val openShortcutsWidth = fontMetrics.stringWidth(openShortcuts)
    val switchShortcutsWidth = fontMetrics.stringWidth(switchShortcuts)
    width = width.coerceAtLeast((openShortcutsWidth.coerceAtLeast(switchShortcutsWidth) * 1.2f).roundToInt())

    // Check if an info panel will actually fit into an editor with some free space around the edges
    if (rectangle.bounds.height < height * 1.2f || rectangle.bounds.width < width * 1.2f) {
      return
    }

    val shape = RoundRectangle2D.Double(centerX - width / 2.0, centerY - height / 2.0, width.toDouble(), height.toDouble(),
                                        arc.toDouble(), arc.toDouble())
    g.color = UIUtil.getLabelBackground()
    g.fill(shape)
    val arrowCenterVShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.center")
    val arrowsVShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.vertical")
    val arrowsHShift = Registry.intValue("ide.splitter.chooser.info.panel.arrows.shift.horizontal")
    val function = Function { icon: Icon -> Point(centerX - icon.iconWidth / 2, centerY - icon.iconHeight / 2 + arrowCenterVShift) }
    val forUpDownIcons = function.apply(AllIcons.Chooser.Top)
    AllIcons.Chooser.Top.paintIcon(component, g, forUpDownIcons.x, forUpDownIcons.y - arrowsVShift)
    AllIcons.Chooser.Bottom.paintIcon(component, g, forUpDownIcons.x, forUpDownIcons.y + arrowsVShift)
    val forLeftRightIcons = function.apply(AllIcons.Chooser.Right)
    AllIcons.Chooser.Right.paintIcon(component, g, forLeftRightIcons.x + arrowsHShift, forLeftRightIcons.y)
    AllIcons.Chooser.Left.paintIcon(component, g, forLeftRightIcons.x - arrowsHShift, forLeftRightIcons.y)
    val textVShift = Registry.intValue("ide.splitter.chooser.info.panel.text.shift")
    val textY = forUpDownIcons.y + AllIcons.Chooser.Bottom.iconHeight + textVShift
    g.color = NamedColorUtil.getInactiveTextColor()
    g.font = font
    g.drawString(openShortcuts, centerX - openShortcutsWidth / 2, textY)
    if (owner.windows().count() > 1) {
      g.drawString(switchShortcuts, centerX - switchShortcutsWidth / 2, textY + fontMetrics.height)
    }
  }

  private fun getShortcut(actionId: String): @NlsSafe String {
    val shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId)
    return KeymapUtil.getKeystrokeText(shortcut?.firstKeyStroke)
  }

  fun positionChanged(position: EditorWindow.RelativePosition) {
    if (this.position == position) {
      return
    }

    showInfoPanel = false
    this.position = position

    updateRectangleAndRepaint()
  }

  fun updateRectangleAndRepaint() {
    rectangle = null
    setNeedsRepaint(true)
    val r = tabbedPane.tabs.dropArea
    TabsUtil.updateBoundsWithDropSide(r, position.swingConstant)
    rectangle = Rectangle2D.Double(r.x.toDouble(), r.y.toDouble(), r.width.toDouble(), r.height.toDouble())
  }
}

internal val TabInfo.composite: EditorComposite
  get() = (component as EditorCompositePanel).composite