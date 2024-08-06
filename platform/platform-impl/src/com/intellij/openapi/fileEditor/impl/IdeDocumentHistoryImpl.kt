// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.treeView.InplaceCommentAppender
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.CommandMerger
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorEventListener
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx.Companion.getInstanceExIfCreated
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl.RecentlyChangedFilesState
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCachePath
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry.Companion.intValue
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.ExternalChangeAction
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.io.EnumeratorLongDescriptor
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.IOUtil
import com.intellij.util.io.PersistentHashMap
import com.intellij.util.io.StorageLockContext
import com.intellij.util.messages.Topic
import com.intellij.util.text.DateFormatUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Deque
import java.util.HashSet
import java.util.function.Predicate

private val LOG = Logger.getInstance(IdeDocumentHistoryImpl::class.java)

private val BACK_QUEUE_LIMIT = intValue("editor.navigation.history.stack.size")
private val CHANGE_QUEUE_LIMIT = intValue("editor.navigation.history.stack.size")

@ApiStatus.Internal
@State(name = "IdeDocumentHistory", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], reportStatistic = false)
open class IdeDocumentHistoryImpl(
  private val project: Project,
  coroutineScope: CoroutineScope,
) : IdeDocumentHistory(), Disposable, PersistentStateComponent<RecentlyChangedFilesState> {
  private var fileDocumentManager: FileDocumentManager? = null

  private val backPlaces = ArrayDeque<PlaceInfo>()
  private val forwardPlaces = ArrayDeque<PlaceInfo>()
  private var backInProgress = false
  private var forwardInProgress = false
  private var currentCommandGroupId: Any? = null
  // weak reference to avoid memory leaks when clients pass some exotic objects as commandId
  private var lastGroupId: Reference<Any?>? = null
  private var registeredBackPlaceInLastGroup = false

  // change's navigation
  private val changePlaces = ArrayDeque<PlaceInfo>()
  private var currentIndex = 0

  private var commandStartPlace: PlaceInfo? = null
  private var currentCommandIsNavigation = false
  private var currentCommandHasChanges = false
  private val changedFilesInCurrentCommand = HashSet<VirtualFile>()
  private var currentCommandHasMoves = false
  private var reallyExcludeCurrentCommandFromNavigation = false

  private val recentFileTimestampMap: SynchronizedClearableLazy<PersistentHashMap<String, Long>>

  @Volatile
  private var state = RecentlyChangedFilesState()

  init {
    val connection = project.getMessageBus().connect(coroutineScope)
    connection.subscribe<BulkFileListener>(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        for (event in events) {
          if (event is VFileDeleteEvent) {
            removeInvalidFilesFromStacks()
            return
          }
        }
      }
    })
    connection.subscribe(CommandListener.TOPIC, object : CommandListener {
      override fun commandStarted(event: CommandEvent) {
        onCommandStarted(event.commandGroupId)
      }

      override fun commandFinished(event: CommandEvent) {
        onCommandFinished(event.project, event.commandGroupId)
      }
    })

    val listener = object : EditorEventListener {
      override fun documentChanged(e: DocumentEvent) {
        val file = getFileDocumentManager().getFile(e.document)
        if (file != null &&
            file !is LightVirtualFile &&
            !ApplicationManager.getApplication().hasWriteAction(ExternalChangeAction::class.java)) {
          ThreadingAssertions.assertEventDispatchThread()
          currentCommandHasChanges = true
          changedFilesInCurrentCommand.add(file)
        }
      }

      override fun caretPositionChanged(e: CaretEvent) {
        if (e.oldPosition.line == e.newPosition.line) {
          return
        }

        val document = e.editor.getDocument()
        if (getFileDocumentManager().getFile(document) != null) {
          currentCommandHasMoves = true
        }
      }
    }

    recentFileTimestampMap = SynchronizedClearableLazy { initRecentFilesTimestampMap(this.project) }

    val multicaster = EditorFactory.getInstance().getEventMulticaster()
    multicaster.addDocumentListener(listener, this)
    multicaster.addCaretListener(listener, this)

    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.addExtensionPointListener(coroutineScope, object : ExtensionPointListener<FileEditorProvider> {
      override fun extensionRemoved(provider: FileEditorProvider, pluginDescriptor: PluginDescriptor) {
        val editorTypeId = provider.getEditorTypeId()
        val clearStatePredicate = Predicate { e: PlaceInfo -> editorTypeId == e.editorTypeId }
        if (changePlaces.removeIf(clearStatePredicate)) {
          currentIndex = changePlaces.size
        }
        backPlaces.removeIf(clearStatePredicate)
        forwardPlaces.removeIf(clearStatePredicate)
        if (commandStartPlace?.editorTypeId == editorTypeId) {
          commandStartPlace = null
        }
      }
    })
  }

  fun appendTimestamp(appender: InplaceCommentAppender, file: VirtualFile) {
    if (!UISettings.getInstance().showInplaceComments) {
      return
    }

    try {
      val timestamp = recentFileTimestampMap.value.get(file.getPath())
      if (timestamp != null) {
        appender.append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        appender.append(DateFormatUtil.formatPrettyDateTime(timestamp), SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
      }
    }
    catch (e: IOException) {
      LOG.info("Cannot get a timestamp from a persistent hash map", e)
    }
  }

  protected open fun getFileEditorManager(): FileEditorManagerEx? = getInstanceExIfCreated(project)

  private fun registerViewed(file: VirtualFile) {
    if (ApplicationManager.getApplication().isUnitTestMode() || !UISettings.getInstance().showInplaceComments) {
      return
    }

    try {
      recentFileTimestampMap.value.put(file.getPath(), System.currentTimeMillis())
    }
    catch (e: IOException) {
      LOG.info("Cannot put a timestamp from a persistent hash map", e)
    }
  }

  @Serializable
  data class RecentlyChangedFilesState(
    @JvmField val changedPaths: List<String> = emptyList<String>(),
  )

  override fun getState(): RecentlyChangedFilesState = state

  override fun loadState(state: RecentlyChangedFilesState) {
    synchronized(this.state) {
      this.state = state
    }
  }

  override fun onSelectionChanged() {
    if (!reallyExcludeCurrentCommandFromNavigation) {
      currentCommandIsNavigation = true
    }
    currentCommandHasMoves = true
  }

  override fun reallyExcludeCurrentCommandAsNavigation() {
    reallyExcludeCurrentCommandFromNavigation = true
    currentCommandIsNavigation = false
  }

  fun onCommandStarted(commandGroupId: Any?) {
    currentCommandGroupId = commandGroupId
    commandStartPlace = getCurrentPlaceInfo()
    currentCommandIsNavigation = false
    currentCommandHasChanges = false
    currentCommandHasMoves = false
    reallyExcludeCurrentCommandFromNavigation = false
    changedFilesInCurrentCommand.clear()
  }

  private fun getCurrentPlaceInfo(): PlaceInfo? {
    val selectedEditorWithProvider = getSelectedEditor() ?: return null
    return createPlaceInfo(selectedEditorWithProvider.fileEditor, selectedEditorWithProvider.provider)
  }

  private fun getPlaceInfoFromFocus(project: Project?): PlaceInfo? {
    val fileEditor = FocusBasedCurrentEditorProvider().getCurrentEditor(project)
    if (fileEditor is TextEditor && fileEditor.isValid()) {
      val file = fileEditor.getFile()
      if (file != null) {
        return PlaceInfo(file,
                         fileEditor.getState(FileEditorStateLevel.NAVIGATION),
                         TextEditorProvider.getInstance().getEditorTypeId(),
                         null, false,
                         getCaretPosition(fileEditor), System.currentTimeMillis())
      }
    }
    return null
  }

  fun onCommandFinished(project: Project?, commandGroupId: Any?) {
    val lastGroupId = lastGroupId?.get()
    if (!CommandMerger.canMergeGroup(commandGroupId, lastGroupId)) {
      registeredBackPlaceInLastGroup = false
    }
    if (commandGroupId !== lastGroupId) {
      this.lastGroupId = commandGroupId?.let { WeakReference(it) }
    }

    val commandStartPlace = commandStartPlace
    if (commandStartPlace != null && currentCommandIsNavigation && currentCommandHasMoves) {
      if (!backInProgress) {
        if (!registeredBackPlaceInLastGroup) {
          registeredBackPlaceInLastGroup = true
          putLastOrMerge(next = commandStartPlace, limit = BACK_QUEUE_LIMIT, isChanged = false, groupId = commandGroupId)
          registerViewed(commandStartPlace.file)
        }
        if (!forwardInProgress) {
          forwardPlaces.clear()
        }
      }
      removeInvalidFilesFromStacks()
    }

    if (currentCommandHasChanges) {
      setCurrentChangePlace(project === this.project)
    }
    else if (currentCommandHasMoves) {
      currentIndex = changePlaces.size
    }
  }

  override fun includeCurrentCommandAsNavigation() {
    if (!reallyExcludeCurrentCommandFromNavigation) {
      currentCommandIsNavigation = true
    }
  }

  override fun setCurrentCommandHasMoves() {
    currentCommandHasMoves = true
  }

  override fun includeCurrentPlaceAsChangePlace() {
    setCurrentChangePlace(false)
  }

  private fun setCurrentChangePlace(acceptPlaceFromFocus: Boolean) {
    var placeInfo = getCurrentPlaceInfo()
    if (placeInfo != null && !changedFilesInCurrentCommand.contains(placeInfo.file)) {
      placeInfo = null
    }
    if (placeInfo == null && acceptPlaceFromFocus) {
      placeInfo = getPlaceInfoFromFocus(project)
    }
    if (placeInfo != null && !changedFilesInCurrentCommand.contains(placeInfo.file)) {
      placeInfo = null
    }
    if (placeInfo == null) {
      return
    }

    val limit = UISettings.getInstance().recentFilesLimit + 1
    synchronized(state) {
      val path = placeInfo.file.getPath()
      val changedPaths = state.changedPaths.toMutableList()
      changedPaths.remove(path)
      changedPaths.add(path)
      while (changedPaths.size > limit) {
        changedPaths.removeAt(0)
      }
      state = RecentlyChangedFilesState(changedPaths)
    }

    putLastOrMerge(next = placeInfo, limit = CHANGE_QUEUE_LIMIT, isChanged = true, groupId = currentCommandGroupId)
    currentIndex = changePlaces.size
  }

  override fun getChangedFiles(): List<VirtualFile> {
    val files = ArrayList<VirtualFile>()
    val localFs = LocalFileSystem.getInstance()
    for (path in state.changedPaths) {
      localFs.findFileByPath(path)?.let {
        files.add(it)
      }
    }
    return files
  }

  fun isRecentlyChanged(file: VirtualFile): Boolean = state.changedPaths.contains(file.getPath())

  override fun clearHistory() {
    backPlaces.clear()
    forwardPlaces.clear()
    changePlaces.clear()

    lastGroupId = null

    currentIndex = 0
    commandStartPlace = null
  }

  override fun back() {
    removeInvalidFilesFromStacks()
    @Suppress("UsePropertyAccessSyntax")
    if (backPlaces.isEmpty()) {
      return
    }

    val info = backPlaces.removeLast()
    project.getMessageBus().syncPublisher(RecentPlacesListener.TOPIC).recentPlaceRemoved(info, false)

    val current = getCurrentPlaceInfo()
    if (current != null) {
      forwardPlaces.add(current)
    }

    backInProgress = true
    try {
      executeCommand(runnable = { gotoPlaceInfo(info) }, name = "", groupId = null)
    }
    finally {
      backInProgress = false
    }
  }

  override fun forward() {
    removeInvalidFilesFromStacks()

    val target = getTargetForwardInfo() ?: return
    forwardInProgress = true
    try {
      executeCommand(runnable = { gotoPlaceInfo(target) }, name = "", groupId = null)
    }
    finally {
      forwardInProgress = false
    }
  }

  private fun getTargetForwardInfo(): PlaceInfo? {
    @Suppress("UsePropertyAccessSyntax")
    if (forwardPlaces.isEmpty()) {
      return null
    }

    var target = forwardPlaces.removeLast()
    val current = getCurrentPlaceInfo()

    @Suppress("UsePropertyAccessSyntax")
    while (!forwardPlaces.isEmpty()) {
      if (current != null && isSame(current, target)) {
        target = forwardPlaces.removeLast()
      }
      else {
        break
      }
    }
    return target
  }

  @Suppress("UsePropertyAccessSyntax")
  override fun isBackAvailable(): Boolean = !backPlaces.isEmpty()

  @Suppress("UsePropertyAccessSyntax")
  override fun isForwardAvailable(): Boolean = !forwardPlaces.isEmpty()

  override fun navigatePreviousChange() {
    removeInvalidFilesFromStacks()
    if (currentIndex == 0) {
      return
    }

    val currentPlace = getCurrentPlaceInfo()
    val changePlaces = getChangePlaces()
    for (i in currentIndex - 1 downTo 0) {
      val info = changePlaces.get(i)
      if (currentPlace == null || !isSame(currentPlace, info)) {
        executeCommand(runnable = { gotoPlaceInfo(info = info, requestFocus = true) }, name = "", groupId = null)
        currentIndex = i
        break
      }
    }
  }

  final override fun navigateNextChange() {
    removeInvalidFilesFromStacks()
    if (currentIndex >= changePlaces.size) {
      return
    }

    val currentPlace = getCurrentPlaceInfo()
    val changePlaces = getChangePlaces()
    for (i in currentIndex until changePlaces.size) {
      val info = changePlaces.get(i)
      if (currentPlace == null || !isSame(currentPlace, info)) {
        executeCommand(Runnable { gotoPlaceInfo(info) }, "", null)
        currentIndex = i + 1
        break
      }
    }
  }

  final override fun getBackPlaces(): List<PlaceInfo> = java.util.List.copyOf(backPlaces)

  final override fun getChangePlaces(): List<PlaceInfo> = java.util.List.copyOf(changePlaces)

  final override fun removeBackPlace(placeInfo: PlaceInfo) {
    removePlaceInfo(placeInfo = placeInfo, places = backPlaces, changed = false)
  }

  final override fun removeChangePlace(placeInfo: PlaceInfo) {
    removePlaceInfo(placeInfo = placeInfo, places = changePlaces, changed = true)
  }

  private fun removePlaceInfo(placeInfo: PlaceInfo, places: MutableCollection<PlaceInfo>, changed: Boolean) {
    val removed = places.remove(placeInfo)
    if (removed) {
      project.getMessageBus().syncPublisher(RecentPlacesListener.Companion.TOPIC).recentPlaceRemoved(placeInfo, changed)
    }
  }

  override fun isNavigatePreviousChangeAvailable(): Boolean {
    return currentIndex > 0
  }

  fun removeInvalidFilesFromStacks() {
    removeInvalidFilesFrom(backPlaces)

    removeInvalidFilesFrom(forwardPlaces)
    if (removeInvalidFilesFrom(changePlaces)) {
      currentIndex = changePlaces.size
    }
  }

  override fun isNavigateNextChangeAvailable(): Boolean = currentIndex < changePlaces.size

  private fun removeInvalidFilesFrom(backPlaces: Deque<PlaceInfo>): Boolean {
    return backPlaces.removeIf { info ->
      val file = info.file
      (file is OptionallyIncluded && !file.isIncludedInDocumentHistory(project)) || !file.isValid()
    }
  }

  interface OptionallyIncluded {
    fun isIncludedInDocumentHistory(project: Project): Boolean
  }

  interface SkipFromDocumentHistory : OptionallyIncluded {
    override fun isIncludedInDocumentHistory(project: Project): Boolean = false
  }

  override fun gotoPlaceInfo(info: PlaceInfo) {
    gotoPlaceInfo(info, ToolWindowManager.getInstance(project).isEditorComponentActive)
  }

  override fun gotoPlaceInfo(info: PlaceInfo, requestFocus: Boolean) {
    val editorManager = getFileEditorManager()!!
    val editorsWithProviders = editorManager.openFile(
      file = info.file,
      window = info.getWindow(),
      options = FileEditorOpenOptions(
        usePreviewTab = info.isPreviewTab,
        requestFocus = requestFocus,
        reuseOpen = true,
        openMode = info.getOpenMode(),
      ),
    )

    editorManager.setSelectedEditor(info.file, info.editorTypeId)

    val list = editorsWithProviders.allEditorsWithProviders
    for (item in list) {
      val typeId = item.provider.getEditorTypeId()
      if (typeId == info.editorTypeId) {
        item.fileEditor.setState(info.navigationState)
      }
    }
  }

  /**
   * @return currently selected FileEditor or null.
   */
  protected open fun getSelectedEditor(): FileEditorWithProvider? {
    val editorManager = getFileEditorManager()
    val file = editorManager?.currentFile ?: return null
    return editorManager.getSelectedEditorWithProvider(file)
  }

  protected open fun createPlaceInfo(fileEditor: FileEditor, fileProvider: FileEditorProvider): PlaceInfo? {
    if (!fileEditor.isValid()) {
      return null
    }

    val editorManager = getFileEditorManager()
    val file = requireNotNull(fileEditor.getFile()) {
      "${fileEditor.javaClass.getName()} getFile() returned null"
    }

    if (file is SkipFromDocumentHistory && !file.isIncludedInDocumentHistory(project)) {
      return null
    }

    val state = fileEditor.getState(FileEditorStateLevel.NAVIGATION)
    val window = editorManager?.currentWindow
    val composite = window?.getComposite(file)
    return PlaceInfo(
      file = file,
      navigationState = state,
      editorTypeId = fileProvider.getEditorTypeId(),
      window = window,
      isPreviewTab = composite != null && composite.isPreview,
      caretPosition = getCaretPosition(fileEditor),
      timeStamp = System.currentTimeMillis(),
    )
  }

  private fun getCaretPosition(fileEditor: FileEditor): RangeMarker? {
    if (fileEditor !is TextEditor) {
      return null
    }

    val editor = fileEditor.getEditor()
    val offset = editor.getCaretModel().offset
    return editor.getDocument().createRangeMarker(offset, offset)
  }

  private fun putLastOrMerge(next: PlaceInfo, limit: Int, isChanged: Boolean, groupId: Any?) {
    val list = if (isChanged) changePlaces else backPlaces
    val messageBus = project.getMessageBus()
    val listener = messageBus.syncPublisher(RecentPlacesListener.TOPIC)
    @Suppress("UsePropertyAccessSyntax")
    if (!list.isEmpty()) {
      val prev = list.getLast()
      if (isSame(prev, next)) {
        val removed = list.removeLast()
        listener.recentPlaceRemoved(removed, isChanged)
      }
    }

    list.add(next)
    listener.recentPlaceAdded(next, isChanged, groupId)
    if (list.size > limit) {
      val first = list.removeFirst()
      listener.recentPlaceRemoved(first, isChanged)
    }
  }

  private fun getFileDocumentManager(): FileDocumentManager {
    var fileDocumentManager = fileDocumentManager
    if (fileDocumentManager == null) {
      fileDocumentManager = FileDocumentManager.getInstance()
      this.fileDocumentManager = fileDocumentManager
    }
    return fileDocumentManager
  }

  class PlaceInfo(
    val file: VirtualFile,
    val navigationState: FileEditorState,
    val editorTypeId: String,
    window: EditorWindow?,
    val isPreviewTab: Boolean,
    val caretPosition: RangeMarker?,
    val timeStamp: Long
  ) {
    private val windowRef = WeakReference(window)

    constructor(
      file: VirtualFile,
      navigationState: FileEditorState,
      editorTypeId: String,
      window: EditorWindow?,
      caretPosition: RangeMarker?
    ) : this(
      file = file,
      navigationState = navigationState,
      editorTypeId = editorTypeId,
      window = window,
      isPreviewTab = false,
      caretPosition = caretPosition,
      timeStamp = -1,
    )

    fun getWindow(): EditorWindow? = windowRef.get()

    override fun toString(): String = file.getName() + " " + navigationState

    @ApiStatus.Internal
    fun getOpenMode(): FileEditorManagerImpl.OpenMode? {
      return (navigationState as? FileEditorStateWithPreferredOpenMode)?.let { return it.openMode }
    }
  }

  override fun dispose() {
    lastGroupId = null
    val map = recentFileTimestampMap.valueIfInitialized ?: return
    try {
      map.close()
    }
    catch (e: IOException) {
      LOG.info("Cannot close persistent viewed files timestamps hash map", e)
    }
  }

  protected open fun executeCommand(runnable: Runnable, name: @NlsContexts.Command String?, groupId: Any?) {
    CommandProcessor.getInstance().executeCommand(project, runnable, name, groupId)
  }

  override fun isSame(first: PlaceInfo, second: PlaceInfo): Boolean {
    if (first.file == second.file) {
      val firstState = first.navigationState
      val secondState = second.navigationState
      return firstState == secondState || firstState.canBeMergedWith(secondState, FileEditorStateLevel.NAVIGATION)
    }
    return false
  }

  /**
   * [RecentPlacesListener] listens recently viewed or changed place adding and removing events.
   */
  interface RecentPlacesListener {
    companion object {
      @Topic.ProjectLevel
      @JvmField
      val TOPIC: Topic<RecentPlacesListener> = Topic(RecentPlacesListener::class.java, Topic.BroadcastDirection.NONE)
    }

    /**
     * Fires on new place info adding into [.changePlaces] or [.backPlaces] infos a list
     *
     * @param changePlace new place info
     * @param isChanged   true if place info was added into the changed infos list [.changePlaces];
     * false if place info was added into the back infos list [.backPlaces]
     */
    @Deprecated("")
    fun recentPlaceAdded(changePlace: PlaceInfo, isChanged: Boolean)

    /**
     * Fires on a place info removing from the [.changePlaces] or the [.backPlaces] infos list
     *
     * @param changePlace place info that was removed
     * @param isChanged   true if place info was removed from the changed infos list [.changePlaces];
     * false if place info was removed from the back infos list [.backPlaces]
     */
    fun recentPlaceRemoved(changePlace: PlaceInfo, isChanged: Boolean)

    /**
     * Fires on new place info adding into [.changePlaces] or [.backPlaces] infos a list
     *
     * @param changePlace new place info
     * @param isChanged   true if place info was added into the changed infos list [.changePlaces];
     * false if place info was added into the back infos list [.backPlaces]
     * @param groupId     groupId of the command that caused the change place addition
     */
    fun recentPlaceAdded(changePlace: PlaceInfo, isChanged: Boolean, groupId: Any?) {
      @Suppress("DEPRECATION")
      recentPlaceAdded(changePlace, isChanged)
    }
  }
}

private fun initRecentFilesTimestampMap(project: Project): PersistentHashMap<String, Long> {
  val file = project.getProjectCachePath("recentFilesTimeStamps.dat")
  try {
    return IOUtil.openCleanOrResetBroken({
                                           PersistentHashMap(file,
                                                             EnumeratorStringDescriptor.INSTANCE,
                                                             EnumeratorLongDescriptor,
                                                             256,
                                                             0,
                                                             StorageLockContext())
                                         }, file)
  }
  catch (e: IOException) {
    LOG.error("Cannot create PersistentHashMap in $file", e)
    throw RuntimeException(e)
  }
}
