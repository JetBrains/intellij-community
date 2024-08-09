// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.openapi.vcs.impl

import com.google.common.collect.HashMultiset
import com.google.common.collect.Multiset
import com.intellij.codeWithMe.ClientId
import com.intellij.diagnostic.ThreadDumper
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictFileStatusProvider
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.getToolWindowFor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.impl.LineStatusTrackerContentLoader.ContentInfo
import com.intellij.openapi.vcs.impl.LineStatusTrackerContentLoader.TrackerContent
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.EventDispatcher
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.isNonModalCommit
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.nio.charset.Charset
import java.util.*
import java.util.function.Supplier

class LineStatusTrackerManager(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) : LineStatusTrackerManagerI, Disposable {
  private val LOCK = Any()
  private var isDisposed = false

  private val trackers = HashMap<Document, TrackerData>()
  private val forcedDocuments = HashMap<Document, Multiset<Any>>()

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  private var partialChangeListsEnabled: Boolean = false
  private val documentsInDefaultChangeList = HashSet<Document>()
  private var clmFreezeCounter: Int = 0

  private val filesWithDamagedInactiveRanges = HashSet<VirtualFile>()
  private val fileStatesAwaitingRefresh = HashMap<VirtualFile, ChangelistsLocalLineStatusTracker.State>()

  private val loader = MyBaseRevisionLoader()

  companion object {
    private val LOG = logger<LineStatusTrackerManager>()

    @JvmStatic
    fun getInstance(project: Project): LineStatusTrackerManagerI = project.service()

    @JvmStatic
    fun getInstanceImpl(project: Project): LineStatusTrackerManager {
      return getInstance(project) as LineStatusTrackerManager
    }
  }

  internal class MyStartupActivity : VcsStartupActivity {
    override val order: Int
      get() = VcsInitObject.OTHER_INITIALIZATION.order

    override suspend fun execute(project: Project) {
      (project.serviceAsync<LineStatusTrackerManagerI>() as LineStatusTrackerManager).startListenForEditors()
    }
  }

  private fun startListenForEditors() {
    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(LineStatusTrackerSettingListener.TOPIC, MyLineStatusTrackerSettingListener())
    connection.subscribe(VcsFreezingProcess.Listener.TOPIC, MyFreezeListener())
    connection.subscribe(CommandListener.TOPIC, MyCommandListener())
    connection.subscribe(ChangeListListener.TOPIC, MyChangeListListener())
    connection.subscribe(ChangeListAvailabilityListener.TOPIC, MyChangeListAvailabilityListener())
    connection.subscribe(FileStatusListener.TOPIC, MyFileStatusListener())
    connection.subscribe(VcsBaseContentProviderListener.TOPIC, BaseContentProviderListener())

    ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      .subscribe(VirtualFileManager.VFS_CHANGES, MyVirtualFileListener())

    LocalLineStatusTrackerProvider.EP_NAME.addChangeListener(Runnable { updateTrackingSettings() }, this)
    VcsBaseContentProvider.EP_NAME.addChangeListener(project, { onEverythingChanged() }, this)

    updatePartialChangeListsAvailability()

    coroutineScope.launch(Dispatchers.EDT) {
      ApplicationManager.getApplication().addApplicationListener(MyApplicationListener(), this@LineStatusTrackerManager)

      EditorFactory.getInstance().eventMulticaster.addDocumentListener(MyDocumentListener(), this@LineStatusTrackerManager)

      writeIntentReadAction {
        MyEditorFactoryListener().install(this@LineStatusTrackerManager)
        onEverythingChanged()
      }

      PartialLineStatusTrackerManagerState.restoreState(project)
    }
  }

  override fun dispose() {
    isDisposed = true
    Disposer.dispose(loader)

    synchronized(LOCK) {
      for ((document, multiset) in forcedDocuments) {
        for (requester in multiset.elementSet()) {
          warn("Tracker is being held on dispose by $requester", document)
        }
      }
      forcedDocuments.clear()

      for (data in trackers.values) {
        unregisterTrackerInCLM(data)
        data.tracker.release()
      }
      trackers.clear()
    }
  }

  override fun getLineStatusTracker(document: Document): LineStatusTracker<*>? {
    synchronized(LOCK) {
      return trackers[document]?.tracker
    }
  }

  override fun getLineStatusTracker(file: VirtualFile): LineStatusTracker<*>? {
    val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return null
    return getLineStatusTracker(document)
  }

  @RequiresEdt
  override fun requestTrackerFor(document: Document, requester: Any) {
    synchronized(LOCK) {
      if (isDisposed) {
        warn("Tracker is being requested after dispose by $requester", document)
        return
      }

      val multiset = forcedDocuments.computeIfAbsent(document) { HashMultiset.create<Any>() }
      multiset.add(requester)

      if (trackers[document] == null) {
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        switchTracker(virtualFile, document)
      }
    }
  }

  @RequiresEdt
  override fun releaseTrackerFor(document: Document, requester: Any) {
    synchronized(LOCK) {
      val multiset = forcedDocuments[document]
      if (multiset == null || !multiset.contains(requester)) {
        warn("Tracker release underflow by $requester", document)
        return
      }

      multiset.remove(requester)

      if (multiset.isEmpty()) {
        forcedDocuments.remove(document)
        checkIfTrackerCanBeReleased(document)
      }
    }
  }

  override fun invokeAfterUpdate(task: Runnable) {
    loader.addAfterUpdateRunnable(task)
  }


  fun getTrackers(): List<LineStatusTracker<*>> {
    synchronized(LOCK) {
      return trackers.values.map { it.tracker }
    }
  }

  fun addTrackerListener(listener: Listener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  open class ListenerAdapter : Listener
  interface Listener : EventListener {
    fun onTrackerAdded(tracker: LineStatusTracker<*>) {
    }

    fun onTrackerRemoved(tracker: LineStatusTracker<*>) {
    }
  }


  @RequiresEdt
  private fun checkIfTrackerCanBeReleased(document: Document) {
    synchronized(LOCK) {
      val data = trackers[document] ?: return

      if (forcedDocuments.containsKey(document)) return

      if (data.tracker is ChangelistsLocalLineStatusTracker) {
        val hasPartialChanges = data.tracker.hasPartialState()
        if (hasPartialChanges) {
          log("checkIfTrackerCanBeReleased - hasPartialChanges", data.tracker.virtualFile)
          return
        }

        val isLoading = loader.hasRequestFor(document)
        if (isLoading) {
          log("checkIfTrackerCanBeReleased - isLoading", data.tracker.virtualFile)
          if (data.tracker.hasPendingPartialState() ||
              fileStatesAwaitingRefresh.containsKey(data.tracker.virtualFile)) {
            log("checkIfTrackerCanBeReleased - has pending state", data.tracker.virtualFile)
            return
          }
        }
      }
      if (data.tracker is SimpleLocalLineStatusTracker) {
        return data.tracker.hasPartialState()
      }

      releaseTracker(document)
    }
  }


  @RequiresEdt
  private fun onEverythingChanged() {
    synchronized(LOCK) {
      if (isDisposed) return
      log("onEverythingChanged", null)

      val files = HashSet<VirtualFile>()

      for (data in trackers.values) {
        files.add(data.tracker.virtualFile)
      }
      for (document in forcedDocuments.keys) {
        val file = FileDocumentManager.getInstance().getFile(document)
        if (file != null) files.add(file)
      }

      for (file in files) {
        onFileChanged(file)
      }
    }
  }

  @RequiresEdt
  private fun onFileChanged(virtualFile: VirtualFile) {
    val document = FileDocumentManager.getInstance().getCachedDocument(virtualFile) ?: return

    synchronized(LOCK) {
      if (isDisposed) return
      log("onFileChanged", virtualFile)
      val tracker = trackers[document]?.tracker

      if (tracker != null || forcedDocuments.containsKey(document)) {
        switchTracker(virtualFile, document, refreshExisting = true)
      }
    }
  }

  private fun registerTrackerInCLM(data: TrackerData) {
    val tracker = data.tracker
    val virtualFile = tracker.virtualFile
    if (tracker !is ChangelistsLocalLineStatusTracker) return

    LOG.assertTrue(virtualFile.isInLocalFileSystem, virtualFile)
    val filePath = VcsUtil.getFilePath(virtualFile)
    if (data.clmFilePath != null) {
      LOG.error("[registerTrackerInCLM] tracker already registered")
      return
    }

    ChangeListManagerImpl.getInstanceImpl(project).registerChangeTracker(filePath, tracker)
    data.clmFilePath = filePath
  }

  private fun unregisterTrackerInCLM(data: TrackerData, wasUnbound: Boolean = false) {
    val tracker = data.tracker
    val virtualFile = tracker.virtualFile
    if (tracker !is ChangelistsLocalLineStatusTracker) return

    LOG.assertTrue(virtualFile.isInLocalFileSystem, virtualFile)
    val filePath = data.clmFilePath
    if (filePath == null) {
      LOG.error("[unregisterTrackerInCLM] tracker is not registered")
      return
    }

    ChangeListManagerImpl.getInstanceImpl(project).unregisterChangeTracker(filePath, tracker)
    data.clmFilePath = null

    val actualFilePath = VcsUtil.getFilePath(virtualFile)
    if (filePath != actualFilePath && !wasUnbound) {
      LOG.error("[unregisterTrackerInCLM] unexpected file path: expected: $filePath, actual: $actualFilePath")
    }
  }

  private fun reregisterTrackerInCLM(data: TrackerData) {
    val tracker = data.tracker
    val virtualFile = tracker.virtualFile
    if (tracker !is ChangelistsLocalLineStatusTracker) return

    LOG.assertTrue(virtualFile.isInLocalFileSystem, virtualFile)
    val oldFilePath = data.clmFilePath
    val newFilePath = VcsUtil.getFilePath(virtualFile)

    if (oldFilePath == null) {
      LOG.error("[reregisterTrackerInCLM] tracker is not registered")
      return
    }

    if (oldFilePath != newFilePath) {
      ChangeListManagerImpl.getInstanceImpl(project).unregisterChangeTracker(oldFilePath, tracker)
      ChangeListManagerImpl.getInstanceImpl(project).registerChangeTracker(newFilePath, tracker)
      data.clmFilePath = newFilePath
    }
  }

  private fun canCreateTrackerFor(virtualFile: VirtualFile, document: Document): Boolean {
    if (isDisposed) return false
    return runReadAction {
      virtualFile.isValid &&
      !virtualFile.fileType.isBinary &&
      !FileDocumentManager.getInstance().isPartialPreviewOfALargeFile(document)
    }
  }

  override fun arePartialChangelistsEnabled(): Boolean {
    if (!partialChangeListsEnabled) return false

    return ProjectLevelVcsManager.getInstance(project).allActiveVcss
      .any { it.arePartialChangelistsSupported() }
  }

  override fun arePartialChangelistsEnabled(virtualFile: VirtualFile): Boolean {
    if (!partialChangeListsEnabled) return false

    val vcs = VcsUtil.getVcsFor(project, virtualFile)
    return vcs != null && vcs.arePartialChangelistsSupported()
  }


  private fun switchTracker(
    virtualFile: VirtualFile, document: Document,
    refreshExisting: Boolean = false,
  ) {
    val provider = getTrackerProvider(virtualFile, document)

    val oldTracker = trackers[document]?.tracker
    if (oldTracker != null && provider != null && provider.isMyTracker(oldTracker)) {
      if (refreshExisting) {
        refreshTracker(oldTracker, provider)
      }
    }
    else {
      releaseTracker(document)
      if (provider != null) installTracker(virtualFile, document, provider)
    }
  }

  private fun installTracker(
    virtualFile: VirtualFile, document: Document,
    provider: LocalLineStatusTrackerProvider,
  ): LocalLineStatusTracker<*>? {
    if (isDisposed) return null
    if (trackers[document] != null) return null

    val tracker = SlowOperations.allowSlowOperations("vcs.line-status-tracker-provider").use {
      provider.createTracker(project, virtualFile) ?: return null
    }
    tracker.mode = getTrackingMode()

    val data = TrackerData(tracker)
    val replacedData = trackers.put(document, data)
    LOG.assertTrue(replacedData == null)

    registerTrackerInCLM(data)
    refreshTracker(tracker, provider)
    eventDispatcher.multicaster.onTrackerAdded(tracker)

    if (clmFreezeCounter > 0) {
      tracker.freeze()
    }

    log("Tracker installed", virtualFile)
    return tracker
  }

  private fun getTrackerProvider(virtualFile: VirtualFile, document: Document): LocalLineStatusTrackerProvider? {
    SlowOperations.allowSlowOperations("vcs.line-status-tracker-provider").use {
      if (!canCreateTrackerFor(virtualFile, document)) {
        return null
      }

      LocalLineStatusTrackerProvider.EP_NAME.findFirstSafe { it.isTrackedFile(project, virtualFile) }?.let {
        return it
      }
      return listOf(ChangelistsLocalStatusTrackerProvider, DefaultLocalStatusTrackerProvider).find {
        it.isTrackedFile(project, virtualFile)
      }
    }
  }

  @RequiresEdt
  private fun releaseTracker(document: Document, wasUnbound: Boolean = false) {
    val data = trackers.remove(document) ?: return

    eventDispatcher.multicaster.onTrackerRemoved(data.tracker)
    unregisterTrackerInCLM(data, wasUnbound)
    data.tracker.release()

    log("Tracker released", data.tracker.virtualFile)
  }

  private fun updatePartialChangeListsAvailability() {
    partialChangeListsEnabled = VcsApplicationSettings.getInstance().ENABLE_PARTIAL_CHANGELISTS &&
                                ChangeListManager.getInstance(project).areChangeListsEnabled()
  }

  private fun updateTrackingSettings() {
    synchronized(LOCK) {
      if (isDisposed) return
      val mode = getTrackingMode()
      for (data in trackers.values) {
        data.tracker.mode = mode
      }
    }

    onEverythingChanged()
  }

  private fun getTrackingMode(): LocalLineStatusTracker.Mode {
    val settings = VcsApplicationSettings.getInstance()
    return LocalLineStatusTracker.Mode(settings.SHOW_LST_GUTTER_MARKERS,
                                       settings.SHOW_LST_ERROR_STRIPE_MARKERS,
                                       settings.SHOW_WHITESPACES_IN_LST)
  }

  @RequiresEdt
  private fun refreshTracker(
    tracker: LocalLineStatusTracker<*>,
    provider: LocalLineStatusTrackerProvider,
  ) {
    if (isDisposed) return
    if (provider !is LineStatusTrackerContentLoader) return
    loader.scheduleRefresh(RefreshRequest(tracker.document, provider))

    log("Refresh queued", tracker.virtualFile)
  }

  private inner class MyBaseRevisionLoader : SingleThreadLoader<RefreshRequest, RefreshData>() {
    fun hasRequestFor(document: Document): Boolean {
      return hasRequest { it.document == document }
    }

    override fun loadRequest(request: RefreshRequest): Result<RefreshData> {
      if (isDisposed) return Result.Canceled()
      val document = request.document
      val virtualFile = FileDocumentManager.getInstance().getFile(document)
      val loader = request.loader

      log("Loading started", virtualFile)

      if (virtualFile == null || !virtualFile.isValid) {
        log("Loading error: virtual file is not valid", virtualFile)
        return Result.Error()
      }

      if (!canCreateTrackerFor(virtualFile, document) || !loader.isTrackedFile(project, virtualFile)) {
        log("Loading error: virtual file is not a tracked file", virtualFile)
        return Result.Error()
      }

      val newContentInfo = loader.getContentInfo(project, virtualFile)
      if (newContentInfo == null) {
        log("Loading error: base revision not found", virtualFile)
        return Result.Error()
      }

      synchronized(LOCK) {
        val data = trackers[document]
        if (data == null) {
          log("Loading cancelled: tracker not found", virtualFile)
          return Result.Canceled()
        }

        if (!loader.shouldBeUpdated(data.contentInfo, newContentInfo)) {
          log("Loading cancelled: no need to update", virtualFile)
          return Result.Canceled()
        }
      }

      val content = loader.loadContent(project, newContentInfo)
      if (content == null) {
        log("Loading error: provider failure", virtualFile)
        return Result.Error()
      }

      log("Loading successful", virtualFile)
      return Result.Success(RefreshData(content, newContentInfo))
    }

    @RequiresEdt
    override fun handleResult(request: RefreshRequest, result: Result<RefreshData>) {
      val document = request.document
      when (result) {
        is Result.Canceled -> handleCanceled(document)
        is Result.Error -> handleError(request, document)
        is Result.Success -> handleSuccess(request, document, result.data)
      }

      checkIfTrackerCanBeReleased(document)
    }

    private fun handleCanceled(document: Document) {
      restorePendingTrackerState(document)
    }

    private fun handleError(request: RefreshRequest, document: Document) {
      synchronized(LOCK) {
        val loader = request.loader
        val data = trackers[document] ?: return
        val tracker = data.tracker

        if (loader.isMyTracker(tracker)) {
          loader.handleLoadingError(tracker)
        }
        data.contentInfo = null
      }
    }

    private fun handleSuccess(request: RefreshRequest, document: Document, refreshData: RefreshData) {
      val virtualFile = FileDocumentManager.getInstance().getFile(document)
      if (virtualFile == null) {
        log("Loading finished: document is not bound", null)
        return
      }

      val loader = request.loader

      val tracker: LocalLineStatusTracker<*>
      synchronized(LOCK) {
        val data = trackers[document]
        if (data == null) {
          log("Loading finished: tracker already released", virtualFile)
          return
        }

        tracker = data.tracker
        if (!loader.isMyTracker(tracker)) {
          log("Loading finished: wrong tracker. tracker: $tracker, loader: $loader", virtualFile)
          return
        }

        if (!loader.shouldBeUpdated(data.contentInfo, refreshData.contentInfo)) {
          log("Loading finished: no need to update", virtualFile)
          return
        }

        data.contentInfo = refreshData.contentInfo
      }

      log("Loading finished: applying content", virtualFile)
      loader.setLoadedContent(tracker, refreshData.content)
      log("Loading finished: success", virtualFile)

      restorePendingTrackerState(document)
    }

    private fun restorePendingTrackerState(document: Document) {
      val tracker = getLineStatusTracker(document)
      if (tracker is ChangelistsLocalLineStatusTracker) {
        val virtualFile = tracker.virtualFile

        val state = synchronized(LOCK) {
          fileStatesAwaitingRefresh.remove(virtualFile) ?: return
        }

        val success = tracker.restoreState(state)
        log("Pending state restored. success - $success", virtualFile)
      }
    }
  }

  /**
   * We can speedup initial content loading if it was already loaded by someone.
   * We do not set 'contentInfo' here to ensure, that following refresh will fix potential inconsistency.
   */
  @RequiresEdt
  @ApiStatus.Internal
  fun offerTrackerContent(document: Document, text: CharSequence) {
    try {
      val tracker: LocalLineStatusTracker<*>
      synchronized(LOCK) {
        val data = trackers[document]
        if (data == null || data.contentInfo != null) return

        tracker = data.tracker
      }

      if (tracker is LocalLineStatusTrackerImpl<*>) {
        ClientId.withClientId(ClientId.localId) {
          tracker.setBaseRevision(text)
          log("Offered content", tracker.virtualFile)
        }
      }
    }
    catch (e: Throwable) {
      LOG.error(e)
    }
  }

  private inner class MyFileStatusListener : FileStatusListener {
    override fun fileStatusesChanged() {
      onEverythingChanged()
    }

    override fun fileStatusChanged(virtualFile: VirtualFile) {
      onFileChanged(virtualFile)
    }
  }

  private inner class BaseContentProviderListener : VcsBaseContentProviderListener {
    override fun onFileBaseContentChanged(file: VirtualFile) {
      runInEdt {
        onFileChanged(file)
      }
    }

    override fun onEverythingChanged() {
      runInEdt {
        onEverythingChanged()
      }
    }
  }

  private inner class MyEditorFactoryListener : EditorFactoryListener {
    fun install(disposable: Disposable) {
      val editorFactory = EditorFactory.getInstance()
      for (editor in editorFactory.editorList) {
        if (isTrackedEditor(editor)) {
          requestTrackerFor(editor.document, editor)
        }
      }
      editorFactory.addEditorFactoryListener(this, disposable)
    }

    override fun editorCreated(event: EditorFactoryEvent) {
      val editor = event.editor
      if (isTrackedEditor(editor)) {
        requestTrackerFor(editor.document, editor)
      }
    }

    override fun editorReleased(event: EditorFactoryEvent) {
      val editor = event.editor
      if (isTrackedEditor(editor)) {
        releaseTrackerFor(editor.document, editor)
      }
    }

    private fun isTrackedEditor(editor: Editor): Boolean {
      // can't filter out "!isInLocalFileSystem" files, custom VcsBaseContentProvider can handle them
      // check for isDisposed - light project in tests
      return !project.isDisposed &&
             (editor.project == null || editor.project == project) &&
             FileDocumentManager.getInstance().getFile(editor.document) != null
    }
  }

  private inner class MyVirtualFileListener : BulkFileListener {
    override fun before(events: List<VFileEvent>) {
      for (event in events) {
        when (event) {
          is VFileDeleteEvent -> handleFileDeletion(event.file)
        }
      }
    }

    override fun after(events: List<VFileEvent>) {
      for (event in events) {
        when (event) {
          is VFilePropertyChangeEvent -> when {
            VirtualFile.PROP_ENCODING == event.propertyName -> onFileChanged(event.file)
            event.isRename -> handleFileMovement(event.file)
          }
          is VFileMoveEvent -> handleFileMovement(event.file)
        }
      }
    }

    private fun handleFileMovement(file: VirtualFile) {
      if (!partialChangeListsEnabled) return

      synchronized(LOCK) {
        forEachTrackerUnder(file) { data ->
          reregisterTrackerInCLM(data)
        }
      }
    }

    private fun handleFileDeletion(file: VirtualFile) {
      if (!partialChangeListsEnabled) return

      synchronized(LOCK) {
        forEachTrackerUnder(file) { data ->
          releaseTracker(data.tracker.document)
        }
      }
    }

    private fun forEachTrackerUnder(file: VirtualFile, action: (TrackerData) -> Unit) {
      if (file.isDirectory) {
        val affected = trackers.values.filter { VfsUtil.isAncestor(file, it.tracker.virtualFile, false) }
        for (data in affected) {
          action(data)
        }
      }
      else {
        val document = FileDocumentManager.getInstance().getCachedDocument(file) ?: return
        val data = trackers[document] ?: return

        action(data)
      }
    }
  }

  internal class MyFileDocumentManagerListener : FileDocumentManagerListener {
    override fun afterDocumentUnbound(file: VirtualFile, document: Document) {
      val projectManager = ProjectManager.getInstanceIfCreated() ?: return
      for (project in projectManager.openProjects) {
        val lstm = project.getServiceIfCreated(LineStatusTrackerManagerI::class.java) as? LineStatusTrackerManager ?: continue
        lstm.releaseTracker(document, wasUnbound = true)
      }
    }
  }

  private inner class MyDocumentListener : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      if (!ApplicationManager.getApplication().isDispatchThread) return // disable for documents forUseInNonAWTThread
      if (!partialChangeListsEnabled || project.isDisposed) return

      val document = event.document
      if (documentsInDefaultChangeList.contains(document)) return

      val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
      if (getLineStatusTracker(document) != null) return

      val provider = getTrackerProvider(virtualFile, document)
      if (provider != ChangelistsLocalStatusTrackerProvider) return

      val changeList = ChangeListManager.getInstance(project).getChangeList(virtualFile)
      val inAnotherChangelist = changeList != null && !ActiveChangeListTracker.getInstance(project).isActiveChangeList(changeList)
      if (inAnotherChangelist) {
        log("Tracker install from DocumentListener: ", virtualFile)

        val tracker = synchronized(LOCK) {
          installTracker(virtualFile, document, provider)
        }
        if (tracker is ChangelistsLocalLineStatusTracker) {
          tracker.replayChangesFromDocumentEvents(listOf(event))
        }
      }
      else {
        documentsInDefaultChangeList.add(document)
      }
    }
  }

  private inner class MyApplicationListener : ApplicationListener {
    override fun afterWriteActionFinished(action: Any) {
      documentsInDefaultChangeList.clear()

      synchronized(LOCK) {
        val documents = trackers.values.map { it.tracker.document }
        for (document in documents) {
          checkIfTrackerCanBeReleased(document)
        }
      }
    }
  }

  private inner class MyLineStatusTrackerSettingListener : LineStatusTrackerSettingListener {
    override fun settingsUpdated() {
      updatePartialChangeListsAvailability()
      updateTrackingSettings()
    }
  }

  private inner class MyChangeListListener : ChangeListAdapter() {
    override fun defaultListChanged(oldDefaultList: ChangeList?, newDefaultList: ChangeList?) {
      runInEdt(ModalityState.any()) {
        if (project.isDisposed) return@runInEdt

        expireInactiveRangesDamagedNotifications()

        EditorFactory.getInstance().allEditors
          .forEach { if (it is EditorEx) it.gutterComponentEx.repaint() }
      }
    }
  }

  private inner class MyChangeListAvailabilityListener : ChangeListAvailabilityListener {
    override fun onBefore() {
      if (ChangeListManager.getInstance(project).areChangeListsEnabled()) {
        val fileStates = getInstanceImpl(project).collectPartiallyChangedFilesStates()
        if (fileStates.isNotEmpty()) {
          PartialLineStatusTrackerManagerState.saveCurrentState(project, fileStates)
        }
      }
    }

    override fun onAfter() {
      updatePartialChangeListsAvailability()
      onEverythingChanged()

      if (ChangeListManager.getInstance(project).areChangeListsEnabled()) {
        PartialLineStatusTrackerManagerState.restoreState(project)
      }
    }
  }

  private inner class MyCommandListener : CommandListener {
    override fun commandFinished(event: CommandEvent) {
      if (!partialChangeListsEnabled) return

      if (CommandProcessor.getInstance().currentCommand == null &&
          !filesWithDamagedInactiveRanges.isEmpty()) {
        showInactiveRangesDamagedNotification()
      }
    }
  }

  class CheckinFactory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
      val project = panel.project
      return object : CheckinHandler() {
        override fun checkinSuccessful() {
          resetExcludedFromCommit()
        }

        override fun checkinFailed(exception: MutableList<VcsException>?) {
          resetExcludedFromCommit()
        }

        private fun resetExcludedFromCommit() {
          runInEdt {
            // TODO Move this to SingleChangeListCommitWorkflow
            if (!project.isDisposed && !panel.isNonModalCommit) getInstanceImpl(project).resetExcludedFromCommitMarkers()
          }
        }
      }
    }
  }

  private inner class MyFreezeListener : VcsFreezingProcess.Listener {
    override fun onFreeze() {
      runReadAction {
        synchronized(LOCK) {
          if (clmFreezeCounter == 0) {
            for (data in trackers.values) {
              try {
                data.tracker.freeze()
              }
              catch (e: Throwable) {
                LOG.error(e)
              }
            }
          }
          clmFreezeCounter++
        }
      }
    }

    override fun onUnfreeze() {
      runInEdt(ModalityState.any()) {
        synchronized(LOCK) {
          clmFreezeCounter--
          if (clmFreezeCounter == 0) {
            for (data in trackers.values) {
              try {
                data.tracker.unfreeze()
              }
              catch (e: Throwable) {
                LOG.error(e)
              }
            }
          }
        }
      }
    }
  }


  private class TrackerData(
    val tracker: LocalLineStatusTracker<*>,
    var contentInfo: ContentInfo? = null,
    var clmFilePath: FilePath? = null,
  )

  private class RefreshRequest(val document: Document, val loader: LineStatusTrackerContentLoader) {
    override fun equals(other: Any?): Boolean = other is RefreshRequest && document == other.document
    override fun hashCode(): Int = document.hashCode()
    override fun toString(): String {
      return "RefreshRequest: " + (FileDocumentManager.getInstance().getFile(document)?.path ?: "unknown") // NON-NLS
    }
  }

  private class RefreshData(
    val content: TrackerContent,
    val contentInfo: ContentInfo,
  )


  private fun log(@NonNls message: String, file: VirtualFile?) {
    if (LOG.isDebugEnabled) {
      if (file != null) {
        LOG.debug(message + "; file: " + file.path)
      }
      else {
        LOG.debug(message)
      }
    }
  }

  private fun warn(@NonNls message: String, document: Document?) {
    val file = document?.let { FileDocumentManager.getInstance().getFile(it) }
    warn(message, file)
  }

  private fun warn(@NonNls message: String, file: VirtualFile?) {
    if (file != null) {
      LOG.warn(message + "; file: " + file.path)
    }
    else {
      LOG.warn(message)
    }
  }


  @RequiresEdt
  fun resetExcludedFromCommitMarkers() {
    synchronized(LOCK) {
      val documents = mutableListOf<Document>()

      for (data in trackers.values) {
        val tracker = data.tracker
        if (tracker is ChangelistsLocalLineStatusTracker) {
          tracker.resetExcludedFromCommitMarkers()
          documents.add(tracker.document)
        }
      }

      for (document in documents) {
        checkIfTrackerCanBeReleased(document)
      }
    }
  }


  internal fun collectPartiallyChangedFilesStates(): List<ChangelistsLocalLineStatusTracker.FullState> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val result = mutableListOf<ChangelistsLocalLineStatusTracker.FullState>()
    synchronized(LOCK) {
      for (data in trackers.values) {
        val tracker = data.tracker
        if (tracker is ChangelistsLocalLineStatusTracker) {
          val hasPartialChanges = tracker.affectedChangeListsIds.size > 1
          if (hasPartialChanges) {
            result.add(tracker.storeTrackerState())
          }
        }
      }
    }
    return result
  }

  @RequiresEdt
  internal fun restoreTrackersForPartiallyChangedFiles(trackerStates: List<ChangelistsLocalLineStatusTracker.State>) {
    runWriteAction {
      synchronized(LOCK) {
        if (isDisposed) return@runWriteAction
        for (state in trackerStates) {
          val virtualFile = state.virtualFile
          val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: continue

          val provider = getTrackerProvider(virtualFile, document)
          if (provider != ChangelistsLocalStatusTrackerProvider) continue

          switchTracker(virtualFile, document)

          val tracker = trackers[document]?.tracker
          if (tracker !is ChangelistsLocalLineStatusTracker) continue

          val isLoading = loader.hasRequestFor(document)
          if (isLoading) {
            fileStatesAwaitingRefresh.put(state.virtualFile, state)
            log("State restoration scheduled", virtualFile)
          }
          else {
            val success = tracker.restoreState(state)
            log("State restored. success - $success", virtualFile)
          }
        }

        loader.addAfterUpdateRunnable(Runnable {
          synchronized(LOCK) {
            log("State restoration finished", null)
            fileStatesAwaitingRefresh.clear()
          }
        })
      }
    }

    onEverythingChanged()
  }


  @RequiresEdt
  internal fun notifyInactiveRangesDamaged(virtualFile: VirtualFile) {
    if (filesWithDamagedInactiveRanges.contains(virtualFile) || virtualFile == FileEditorManagerEx.getInstanceEx(project).currentFile) {
      return
    }
    filesWithDamagedInactiveRanges.add(virtualFile)
  }

  private fun showInactiveRangesDamagedNotification() {
    val currentNotifications = NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(InactiveRangesDamagedNotification::class.java, project)

    val lastNotification = currentNotifications.lastOrNull { !it.isExpired }
    if (lastNotification != null) filesWithDamagedInactiveRanges.addAll(lastNotification.virtualFiles)

    currentNotifications.forEach { it.expire() }

    val files = filesWithDamagedInactiveRanges.toSet()
    filesWithDamagedInactiveRanges.clear()

    InactiveRangesDamagedNotification(project, files).notify(project)
  }

  @RequiresEdt
  private fun expireInactiveRangesDamagedNotifications() {
    filesWithDamagedInactiveRanges.clear()

    val currentNotifications = NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(InactiveRangesDamagedNotification::class.java, project)
    currentNotifications.forEach { it.expire() }
  }

  private class InactiveRangesDamagedNotification(project: Project, val virtualFiles: Set<VirtualFile>)
    : Notification(VcsNotifier.standardNotification().displayId,
                   VcsBundle.message("lst.inactive.ranges.damaged.notification"),
                   NotificationType.INFORMATION) {
    init {
      icon = AllIcons.Toolwindows.ToolWindowChanges
      setDisplayId(VcsNotificationIdsHolder.INACTIVE_RANGES_DAMAGED)
      addAction(NotificationAction.createSimple(
        Supplier { VcsBundle.message("action.NotificationAction.InactiveRangesDamagedNotification.text.view.changes") },
        Runnable {
          val defaultList = ChangeListManager.getInstance(project).defaultChangeList
          val changes = defaultList.changes.filter { virtualFiles.contains(it.virtualFile) }

          val window = getToolWindowFor(project, LOCAL_CHANGES)
          window?.activate { ChangesViewManager.getInstance(project).selectChanges(changes) }
          expire()
        }))
    }
  }


  @TestOnly
  fun waitUntilBaseContentsLoaded() {
    assert(ApplicationManager.getApplication().isUnitTestMode)

    if (ApplicationManager.getApplication().isDispatchThread) {
      UIUtil.dispatchAllInvocationEvents()
    }

    val semaphore = Semaphore()
    semaphore.down()

    loader.addAfterUpdateRunnable(Runnable {
      semaphore.up()
    })

    val start = System.currentTimeMillis()
    while (true) {
      if (ApplicationManager.getApplication().isDispatchThread) {
        UIUtil.dispatchAllInvocationEvents()
      }
      if (semaphore.waitFor(10)) {
        return
      }
      if (System.currentTimeMillis() - start > 10000) {
        loader.dumpInternalState()
        System.err.println(ThreadDumper.dumpThreadsToString())
        throw IllegalStateException("Couldn't await base contents") // NON-NLS
      }
    }
  }

  @TestOnly
  fun releaseAllTrackers() {
    synchronized(LOCK) {
      forcedDocuments.clear()

      for (data in trackers.values) {
        unregisterTrackerInCLM(data)
        data.tracker.release()
      }
      trackers.clear()
    }
  }
}


/**
 * Single threaded queue with the following properties:
 * - Ignores duplicated requests (the first queued is used).
 * - Allows to check whether request is scheduled or is waiting for completion.
 * - Notifies callbacks when queue is exhausted.
 */
private abstract class SingleThreadLoader<Request, T> : Disposable {
  companion object {
    private val LOG = logger<SingleThreadLoader<*, *>>()
  }

  private val LOCK: Any = Any()

  private val taskQueue = ArrayDeque<Request>()
  private val waitingForRefresh = HashSet<Request>()

  private val callbacksWaitingUpdateCompletion = ArrayList<Runnable>()

  private var isScheduled: Boolean = false
  private var isDisposed: Boolean = false

  @Suppress("DEPRECATION")
  private val scope = (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope().childScope()

  @RequiresBackgroundThread
  protected abstract fun loadRequest(request: Request): Result<T>

  @RequiresEdt
  protected abstract fun handleResult(request: Request, result: Result<T>)

  @RequiresEdt
  fun scheduleRefresh(request: Request) {
    if (isDisposed) {
      return
    }

    synchronized(LOCK) {
      if (taskQueue.contains(request)) {
        return
      }

      taskQueue.add(request)
      schedule()
    }
  }

  @RequiresEdt
  override fun dispose() {
    val callbacks = mutableListOf<Runnable>()
    synchronized(LOCK) {
      isDisposed = true
      taskQueue.clear()
      waitingForRefresh.clear()
      scope.cancel()

      callbacks += callbacksWaitingUpdateCompletion
      callbacksWaitingUpdateCompletion.clear()
    }

    executeCallbacks(callbacksWaitingUpdateCompletion)
  }

  @RequiresEdt
  protected fun hasRequest(condition: (Request) -> Boolean): Boolean {
    synchronized(LOCK) {
      return taskQueue.any(condition) ||
             waitingForRefresh.any(condition)
    }
  }

  @CalledInAny
  fun addAfterUpdateRunnable(task: Runnable) {
    val updateScheduled = putRunnableIfUpdateScheduled(task)
    if (updateScheduled) return

    runInEdt(ModalityState.any()) {
      if (!putRunnableIfUpdateScheduled(task)) {
        task.run()
      }
    }
  }

  private fun putRunnableIfUpdateScheduled(task: Runnable): Boolean {
    synchronized(LOCK) {
      if (taskQueue.isEmpty() && waitingForRefresh.isEmpty()) return false
      callbacksWaitingUpdateCompletion.add(task)
      return true
    }
  }


  private fun schedule() {
    if (isDisposed) {
      return
    }

    synchronized(LOCK) {
      if (isScheduled || taskQueue.isEmpty()) {
        return
      }

      isScheduled = true
      scope.launch {
        ClientId.withClientId(ClientId.localId) {
          coroutineToIndicator {
            handleRequests()
          }
        }
      }
    }
  }

  private fun handleRequests() {
    while (true) {
      val request = synchronized(LOCK) {
        val request = taskQueue.poll()

        if (isDisposed || request == null) {
          isScheduled = false
          return
        }

        waitingForRefresh.add(request)
        return@synchronized request
      }

      handleSingleRequest(request)
    }
  }

  private fun handleSingleRequest(request: Request) {
    val result: Result<T> = try {
      loadRequest(request)
    }
    catch (e: ProcessCanceledException) {
      Result.Canceled()
    }
    catch (e: Throwable) {
      LOG.error(e)
      Result.Error()
    }

    runInEdt(ModalityState.any()) {
      try {
        synchronized(LOCK) {
          waitingForRefresh.remove(request)
        }

        handleResult(request, result)
      }
      finally {
        notifyTrackerRefreshed()
      }
    }
  }

  @RequiresEdt
  private fun notifyTrackerRefreshed() {
    if (isDisposed) return

    val callbacks = mutableListOf<Runnable>()
    synchronized(LOCK) {
      if (taskQueue.isEmpty() && waitingForRefresh.isEmpty()) {
        callbacks += callbacksWaitingUpdateCompletion
        callbacksWaitingUpdateCompletion.clear()
      }
    }

    executeCallbacks(callbacks)
  }

  @RequiresEdt
  private fun executeCallbacks(callbacks: List<Runnable>) {
    for (callback in callbacks) {
      try {
        callback.run()
      }
      catch (ignore: ProcessCanceledException) {
      }
      catch (e: Throwable) {
        LOG.error(e)
      }
    }
  }

  @TestOnly
  fun dumpInternalState() {
    synchronized(LOCK) {
      LOG.debug("isScheduled - $isScheduled")
      LOG.debug("pending callbacks: ${callbacksWaitingUpdateCompletion.size}")

      taskQueue.forEach {
        LOG.debug("pending task: ${it}")
      }
      waitingForRefresh.forEach {
        LOG.debug("waiting refresh: ${it}")
      }
    }
  }
}

private sealed class Result<T> {
  class Success<T>(val data: T) : Result<T>()
  class Canceled<T> : Result<T>()
  class Error<T> : Result<T>()
}

private object ChangelistsLocalStatusTrackerProvider : BaseRevisionStatusTrackerContentLoader() {
  override fun isTrackedFile(project: Project, file: VirtualFile): Boolean {
    if (!LineStatusTrackerManager.getInstance(project).arePartialChangelistsEnabled(file)) return false
    if (!super.isTrackedFile(project, file)) return false

    val status = FileStatusManager.getInstance(project).getStatus(file)
    if (status != FileStatus.MODIFIED &&
        status != ChangelistConflictFileStatusProvider.MODIFIED_OUTSIDE &&
        status != FileStatus.NOT_CHANGED) return false

    val change = ChangeListManager.getInstance(project).getChange(file)
    return change == null ||
           change.javaClass == Change::class.java &&
           (change.type == Change.Type.MODIFICATION || change.type == Change.Type.MOVED) &&
           change.afterRevision is CurrentContentRevision
  }

  override fun isMyTracker(tracker: LocalLineStatusTracker<*>): Boolean = tracker is ChangelistsLocalLineStatusTracker

  override fun createTracker(project: Project, file: VirtualFile): LocalLineStatusTracker<*>? {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    return ChangelistsLocalLineStatusTracker.createTracker(project, document, file)
  }
}

private object DefaultLocalStatusTrackerProvider : BaseRevisionStatusTrackerContentLoader() {
  override fun isTrackedFile(project: Project, file: VirtualFile): Boolean {
    val vcsFile = VcsUtil.resolveSymlinkIfNeeded(project, file)
    return super.isTrackedFile(project, vcsFile)
  }

  override fun getContentInfo(project: Project, file: VirtualFile): ContentInfo? {
    val vcsFile = VcsUtil.resolveSymlinkIfNeeded(project, file)
    return super.getContentInfo(project, vcsFile)
  }

  override fun isMyTracker(tracker: LocalLineStatusTracker<*>): Boolean = tracker is SimpleLocalLineStatusTracker

  override fun createTracker(project: Project, file: VirtualFile): LocalLineStatusTracker<*>? {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    return SimpleLocalLineStatusTracker.createTracker(project, document, file)
  }
}

private abstract class BaseRevisionStatusTrackerContentLoader : LineStatusTrackerContentLoader {
  override fun isTrackedFile(project: Project, file: VirtualFile): Boolean {
    if (!LineStatusTrackerBaseContentUtil.isSupported(project, file)) return false
    return LineStatusTrackerBaseContentUtil.isTracked(project, file)
  }

  override fun getContentInfo(project: Project, file: VirtualFile): ContentInfo? {
    val baseContent = LineStatusTrackerBaseContentUtil.getBaseRevision(project, file) ?: return null
    return BaseRevisionContentInfo(baseContent, file.charset)
  }

  override fun shouldBeUpdated(oldInfo: ContentInfo?, newInfo: ContentInfo): Boolean {
    newInfo as BaseRevisionContentInfo
    return oldInfo == null ||
           oldInfo !is BaseRevisionContentInfo ||
           oldInfo.baseContent.revisionNumber != newInfo.baseContent.revisionNumber ||
           oldInfo.baseContent.revisionNumber == VcsRevisionNumber.NULL ||
           oldInfo.charset != newInfo.charset
  }

  override fun loadContent(project: Project, info: ContentInfo): TrackerContent? {
    info as BaseRevisionContentInfo
    val lastUpToDateContent = info.baseContent.loadContent() ?: return null
    val correctedText = StringUtil.convertLineSeparators(lastUpToDateContent)
    return BaseRevisionContent(correctedText)
  }

  override fun setLoadedContent(tracker: LocalLineStatusTracker<*>, content: TrackerContent) {
    tracker as LocalLineStatusTrackerImpl<*>
    content as BaseRevisionContent
    tracker.setBaseRevision(content.text)
  }

  override fun handleLoadingError(tracker: LocalLineStatusTracker<*>) {
    tracker as LocalLineStatusTrackerImpl<*>
    tracker.dropBaseRevision()
  }

  private class BaseRevisionContentInfo(val baseContent: VcsBaseContentProvider.BaseContent, val charset: Charset) : ContentInfo
  private class BaseRevisionContent(val text: CharSequence) : TrackerContent
}

/**
 * Allows overriding created trackers for partucular files.
 *
 * Trackers are created on EDT on request (ex: when [Editor] is opened).
 * Providers may implement [LineStatusTrackerContentLoader] to use [LineStatusTrackerManager] mechanism for loading necessary
 * information on a pooled thread.
 *
 * @see LineStatusTrackerSettingListener
 * @see VcsBaseContentProvider
 */
interface LocalLineStatusTrackerProvider {
  fun isTrackedFile(project: Project, file: VirtualFile): Boolean
  fun isMyTracker(tracker: LocalLineStatusTracker<*>): Boolean

  @RequiresEdt
  fun createTracker(project: Project, file: VirtualFile): LocalLineStatusTracker<*>?

  companion object {
    internal val EP_NAME =
      ExtensionPointName<LocalLineStatusTrackerProvider>("com.intellij.openapi.vcs.impl.LocalLineStatusTrackerProvider")
  }
}

interface LineStatusTrackerContentLoader : LocalLineStatusTrackerProvider {
  fun getContentInfo(project: Project, file: VirtualFile): ContentInfo?
  fun shouldBeUpdated(oldInfo: ContentInfo?, newInfo: ContentInfo): Boolean
  fun loadContent(project: Project, info: ContentInfo): TrackerContent?

  fun setLoadedContent(tracker: LocalLineStatusTracker<*>, content: TrackerContent)
  fun handleLoadingError(tracker: LocalLineStatusTracker<*>)

  interface ContentInfo
  interface TrackerContent
}
