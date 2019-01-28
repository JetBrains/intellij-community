// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.google.common.collect.HashMultiset
import com.google.common.collect.Multiset
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
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictFileStatusProvider
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ex.ChangelistsLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.LocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.*
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import org.jetbrains.annotations.TestOnly
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.Future

class LineStatusTrackerManager(
  private val project: Project,
  private val application: Application,
  private val statusProvider: VcsBaseContentProvider,
  private val changeListManager: ChangeListManagerImpl,
  private val fileDocumentManager: FileDocumentManager,
  private val fileEditorManager: FileEditorManagerEx,
  @Suppress("UNUSED_PARAMETER") makeSureIndexIsInitializedFirst: DirectoryIndex
) : BaseComponent, LineStatusTrackerManagerI, Disposable {

  private val LOCK = Any()
  private var isDisposed = false

  private val trackers = HashMap<Document, TrackerData>()
  private val forcedDocuments = HashMap<Document, Multiset<Any>>()

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  private var partialChangeListsEnabled = VcsApplicationSettings.getInstance().ENABLE_PARTIAL_CHANGELISTS && Registry.`is`("vcs.enable.partial.changelists")
  private val documentsInDefaultChangeList = HashSet<Document>()
  private var clmFreezeCounter: Int = 0

  private val filesWithDamagedInactiveRanges = HashSet<VirtualFile>()
  private val fileStatesAwaitingRefresh = HashMap<VirtualFile, ChangelistsLocalLineStatusTracker.State>()

  private val loader: SingleThreadLoader<RefreshRequest, RefreshData> = MyBaseRevisionLoader()

  companion object {
    private val LOG = Logger.getInstance(LineStatusTrackerManager::class.java)

    @JvmStatic
    fun getInstance(project: Project): LineStatusTrackerManagerI {
      return project.getComponent(LineStatusTrackerManagerI::class.java)
    }

    @JvmStatic
    fun getInstanceImpl(project: Project): LineStatusTrackerManager {
      return getInstance(project) as LineStatusTrackerManager
    }
  }

  override fun initComponent() {
    StartupManager.getInstance(project).registerPreStartupActivity {
      if (isDisposed) return@registerPreStartupActivity

      application.addApplicationListener(MyApplicationListener(), this)

      val busConnection = project.messageBus.connect(this)
      busConnection.subscribe(LineStatusTrackerSettingListener.TOPIC, MyLineStatusTrackerSettingListener())
      busConnection.subscribe(VcsFreezingProcess.Listener.TOPIC, MyFreezeListener())

      val fsManager = FileStatusManager.getInstance(project)
      fsManager.addFileStatusListener(MyFileStatusListener(), this)

      val editorFactory = EditorFactory.getInstance()
      editorFactory.addEditorFactoryListener(MyEditorFactoryListener(), this)
      editorFactory.eventMulticaster.addDocumentListener(MyDocumentListener(), this)

      changeListManager.addChangeListListener(MyChangeListListener())

      val virtualFileManager = VirtualFileManager.getInstance()
      virtualFileManager.addVirtualFileListener(MyVirtualFileListener(), this)

      busConnection.subscribe(CommandListener.TOPIC, MyCommandListener())
    }
  }

  override fun dispose() {
    isDisposed = true

    synchronized(LOCK) {
      for ((document, multiset) in forcedDocuments) {
        for (requester in multiset.elementSet()) {
          warn("Tracker for is being held on dispose by $requester", document)
        }
      }
      forcedDocuments.clear()

      for (data in trackers.values) {
        unregisterTrackerInCLM(data)
        data.tracker.release()
      }
      trackers.clear()

      loader.dispose()
    }
  }

  override fun getLineStatusTracker(document: Document): LineStatusTracker<*>? {
    synchronized(LOCK) {
      return trackers[document]?.tracker
    }
  }

  override fun getLineStatusTracker(file: VirtualFile): LineStatusTracker<*>? {
    val document = fileDocumentManager.getCachedDocument(file) ?: return null
    return getLineStatusTracker(document)
  }

  @CalledInAwt
  override fun requestTrackerFor(document: Document, requester: Any) {
    application.assertIsDispatchThread()
    synchronized(LOCK) {
      val multiset = forcedDocuments.computeIfAbsent(document) { HashMultiset.create<Any>() }
      multiset.add(requester)

      if (trackers[document] == null) {
        val virtualFile = fileDocumentManager.getFile(document) ?: return
        installTracker(virtualFile, document)
      }
    }
  }

  @CalledInAwt
  override fun releaseTrackerFor(document: Document, requester: Any) {
    application.assertIsDispatchThread()
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


  @CalledInAwt
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

        val isLoading = loader.hasRequest(RefreshRequest(document))
        if (isLoading) {
          log("checkIfTrackerCanBeReleased - isLoading", data.tracker.virtualFile)
          return
        }
      }

      releaseTracker(document)
    }
  }


  @CalledInAwt
  private fun onEverythingChanged() {
    application.assertIsDispatchThread()
    synchronized(LOCK) {
      if (isDisposed) return
      log("onEverythingChanged", null)

      val files = HashSet<VirtualFile>()

      for (data in trackers.values) {
        files.add(data.tracker.virtualFile)
      }
      for (document in forcedDocuments.keys) {
        val file = fileDocumentManager.getFile(document)
        if (file != null) files.add(file)
      }

      for (file in files) {
        onFileChanged(file)
      }
    }
  }

  @CalledInAwt
  private fun onFileChanged(virtualFile: VirtualFile) {
    val document = fileDocumentManager.getCachedDocument(virtualFile) ?: return

    synchronized(LOCK) {
      if (isDisposed) return
      log("onFileChanged", virtualFile)
      val tracker = trackers[document]?.tracker

      if (tracker == null) {
        if (forcedDocuments.containsKey(document)) {
          installTracker(virtualFile, document)
        }
      }
      else {
        val isPartialTrackerExpected = canCreatePartialTrackerFor(virtualFile)
        val isPartialTracker = tracker is ChangelistsLocalLineStatusTracker

        if (isPartialTrackerExpected == isPartialTracker) {
          refreshTracker(tracker)
        }
        else {
          releaseTracker(document)
          installTracker(virtualFile, document)
        }
      }
    }
  }

  private fun registerTrackerInCLM(data: TrackerData) {
    val tracker = data.tracker
    if (tracker !is ChangelistsLocalLineStatusTracker) return

    val filePath = VcsUtil.getFilePath(tracker.virtualFile)
    if (data.clmFilePath != null) {
      LOG.error("[registerTrackerInCLM] tracker already registered")
      return
    }

    changeListManager.registerChangeTracker(filePath, tracker)
    data.clmFilePath = filePath
  }

  private fun unregisterTrackerInCLM(data: TrackerData) {
    val tracker = data.tracker
    if (tracker !is ChangelistsLocalLineStatusTracker) return

    val filePath = data.clmFilePath
    if (filePath == null) {
      LOG.error("[unregisterTrackerInCLM] tracker is not registered")
      return
    }

    changeListManager.unregisterChangeTracker(filePath, tracker)
    data.clmFilePath = null

    val actualFilePath = VcsUtil.getFilePath(tracker.virtualFile)
    if (filePath != actualFilePath) {
      LOG.error("[unregisterTrackerInCLM] unexpected file path: expected: $filePath, actual: $actualFilePath")
    }
  }

  private fun reregisterTrackerInCLM(data: TrackerData) {
    val tracker = data.tracker
    if (tracker !is ChangelistsLocalLineStatusTracker) return

    val oldFilePath = data.clmFilePath
    val newFilePath = VcsUtil.getFilePath(tracker.virtualFile)

    if (oldFilePath == null) {
      LOG.error("[reregisterTrackerInCLM] tracker is not registered")
      return
    }

    if (oldFilePath != newFilePath) {
      changeListManager.unregisterChangeTracker(oldFilePath, tracker)
      changeListManager.registerChangeTracker(newFilePath, tracker)
      data.clmFilePath = newFilePath
    }
  }


  private fun canGetBaseRevisionFor(virtualFile: VirtualFile?): Boolean {
    if (isDisposed) return false
    if (virtualFile == null || virtualFile is LightVirtualFile) return false
    if (runReadAction { !virtualFile.isValid || virtualFile.fileType.isBinary || FileUtilRt.isTooLarge(virtualFile.length) }) return false
    if (!statusProvider.isSupported(virtualFile)) return false

    val status = FileStatusManager.getInstance(project).getStatus(virtualFile)
    if (status == FileStatus.ADDED ||
        status == FileStatus.DELETED ||
        status == FileStatus.UNKNOWN ||
        status == FileStatus.IGNORED) {
      return false
    }
    return true
  }

  private fun canCreatePartialTrackerFor(virtualFile: VirtualFile): Boolean {
    if (!arePartialChangelistsEnabled(virtualFile)) return false

    val status = FileStatusManager.getInstance(project).getStatus(virtualFile)
    if (status != FileStatus.MODIFIED &&
        status != ChangelistConflictFileStatusProvider.MODIFIED_OUTSIDE &&
        status != FileStatus.NOT_CHANGED) return false

    val change = ChangeListManager.getInstance(project).getChange(virtualFile)
    return change != null && change.javaClass == Change::class.java &&
           (change.type == Change.Type.MODIFICATION || change.type == Change.Type.MOVED) &&
           change.afterRevision is CurrentContentRevision
  }

  override fun arePartialChangelistsEnabled(virtualFile: VirtualFile): Boolean {
    if (!partialChangeListsEnabled) return false
    if (getTrackingMode() == LocalLineStatusTracker.Mode.SILENT) return false

    val vcs = VcsUtil.getVcsFor(project, virtualFile)
    return vcs != null && vcs.arePartialChangelistsSupported()
  }


  @CalledInAwt
  private fun installTracker(virtualFile: VirtualFile, document: Document) {
    if (!canGetBaseRevisionFor(virtualFile)) return

    doInstallTracker(virtualFile, document)
  }

  @CalledInAwt
  private fun doInstallTracker(virtualFile: VirtualFile, document: Document): LineStatusTracker<*>? {
    application.assertIsDispatchThread()
    synchronized(LOCK) {
      if (isDisposed) return null
      if (trackers[document] != null) return null

      val tracker = if (canCreatePartialTrackerFor(virtualFile)) {
        ChangelistsLocalLineStatusTracker.createTracker(project, document, virtualFile, getTrackingMode())
      }
      else {
        SimpleLocalLineStatusTracker.createTracker(project, document, virtualFile, getTrackingMode())
      }

      val data = TrackerData(tracker)
      trackers.put(document, data)

      registerTrackerInCLM(data)
      refreshTracker(tracker)
      eventDispatcher.multicaster.onTrackerAdded(tracker)

      if (clmFreezeCounter > 0) {
        tracker.freeze()
      }

      log("Tracker installed", virtualFile)
      return tracker
    }
  }

  @CalledInAwt
  private fun releaseTracker(document: Document) {
    application.assertIsDispatchThread()
    synchronized(LOCK) {
      if (isDisposed) return
      val data = trackers.remove(document) ?: return

      eventDispatcher.multicaster.onTrackerRemoved(data.tracker)
      unregisterTrackerInCLM(data)
      data.tracker.release()

      log("Tracker released", data.tracker.virtualFile)
    }
  }

  private fun updateTrackingModes() {
    synchronized(LOCK) {
      if (isDisposed) return
      val mode = getTrackingMode()
      val trackers = trackers.values.map { it.tracker }
      for (tracker in trackers) {
        val document = tracker.document
        val virtualFile = tracker.virtualFile

        val isPartialTrackerExpected = canCreatePartialTrackerFor(virtualFile)
        val isPartialTracker = tracker is ChangelistsLocalLineStatusTracker

        if (isPartialTrackerExpected == isPartialTracker) {
          tracker.mode = mode
        }
        else {
          releaseTracker(document)
          installTracker(virtualFile, document)
        }
      }
    }
  }

  private fun getTrackingMode(): LocalLineStatusTracker.Mode {
    val settings = VcsApplicationSettings.getInstance()
    if (!settings.SHOW_LST_GUTTER_MARKERS) return LocalLineStatusTracker.Mode.SILENT
    if (settings.SHOW_WHITESPACES_IN_LST) return LocalLineStatusTracker.Mode.SMART
    return LocalLineStatusTracker.Mode.DEFAULT
  }

  @CalledInAwt
  private fun refreshTracker(tracker: LocalLineStatusTracker<*>) {
    synchronized(LOCK) {
      if (isDisposed) return
      loader.scheduleRefresh(RefreshRequest(tracker.document))

      log("Refresh queued", tracker.virtualFile)
    }
  }

  private inner class MyBaseRevisionLoader : SingleThreadLoader<RefreshRequest, RefreshData>() {
    override fun loadRequest(request: RefreshRequest): Result<RefreshData> {
      if (isDisposed) return Result.Canceled()
      val document = request.document
      val virtualFile = fileDocumentManager.getFile(document)

      log("Loading started", virtualFile)

      if (virtualFile == null || !virtualFile.isValid) {
        log("Loading error: virtual file is not valid", virtualFile)
        return Result.Error()
      }

      if (!canGetBaseRevisionFor(virtualFile)) {
        log("Loading error: cant get base revision", virtualFile)
        return Result.Error()
      }

      val baseContent = statusProvider.getBaseRevision(virtualFile)
      if (baseContent == null) {
        log("Loading error: base revision not found", virtualFile)
        return Result.Error()
      }

      val newContentInfo = ContentInfo(baseContent.revisionNumber, virtualFile.charset)

      synchronized(LOCK) {
        val data = trackers[document]
        if (data == null) {
          log("Loading cancelled: tracker not found", virtualFile)
          return Result.Canceled()
        }

        if (!shouldBeUpdated(data.contentInfo, newContentInfo)) {
          log("Loading cancelled: no need to update", virtualFile)
          return Result.Canceled()
        }
      }

      val lastUpToDateContent = baseContent.loadContent()
      if (lastUpToDateContent == null) {
        log("Loading error: provider failure", virtualFile)
        return Result.Error()
      }

      val converted = StringUtil.convertLineSeparators(lastUpToDateContent)
      log("Loading successful", virtualFile)

      return Result.Success(RefreshData(converted, newContentInfo))
    }

    @CalledInAwt
    override fun handleResult(request: RefreshRequest, result: Result<RefreshData>) {
      val document = request.document
      when (result) {
        is Result.Canceled -> handleCanceled(document)
        is Result.Error -> handleError(document)
        is Result.Success -> handleSuccess(document, result.data)
      }

      checkIfTrackerCanBeReleased(document)
    }

    private fun LineStatusTrackerManager.handleCanceled(document: Document) {
      val virtualFile = fileDocumentManager.getFile(document) ?: return

      val state = synchronized(LOCK) {
        fileStatesAwaitingRefresh.remove(virtualFile) ?: return
      }

      val tracker = getLineStatusTracker(document)
      if (tracker is ChangelistsLocalLineStatusTracker) {
        tracker.restoreState(state)
        log("Loading canceled: state restored", virtualFile)
      }
    }

    private fun handleError(document: Document) {
      synchronized(LOCK) {
        val data = trackers[document] ?: return

        data.tracker.dropBaseRevision()
        data.contentInfo = null
      }
    }

    private fun LineStatusTrackerManager.handleSuccess(document: Document,
                                                       refreshData: RefreshData) {
      val virtualFile = fileDocumentManager.getFile(document)!!

      synchronized(LOCK) {
        val data = trackers[document]
        if (data == null) {
          log("Loading finished: tracker already released", virtualFile)
          return
        }
        if (!shouldBeUpdated(data.contentInfo, refreshData.info)) {
          log("Loading finished: no need to update", virtualFile)
          return
        }

        data.contentInfo = refreshData.info
      }

      val tracker = getLineStatusTracker(document) as LocalLineStatusTracker<*>
      tracker.setBaseRevision(refreshData.text)
      log("Loading finished: success", virtualFile)

      if (tracker is ChangelistsLocalLineStatusTracker) {
        val state = fileStatesAwaitingRefresh.remove(tracker.virtualFile)
        if (state != null) {
          tracker.restoreState(state)
          log("Loading finished: state restored", virtualFile)
        }
      }
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

  private inner class MyEditorFactoryListener : EditorFactoryListener {
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
      if (editor.project != null && editor.project != project) return false
      if (editor.editorKind == EditorKind.PREVIEW_UNDER_READ_ACTION) return false
      return true
    }
  }

  private inner class MyVirtualFileListener : VirtualFileListener {
    override fun beforePropertyChange(event: VirtualFilePropertyEvent) {
      if (VirtualFile.PROP_ENCODING == event.propertyName) {
        onFileChanged(event.file)
      }
    }

    override fun propertyChanged(event: VirtualFilePropertyEvent) {
      if (VirtualFile.PROP_NAME == event.propertyName) {
        handleFileMovement(event.file)
      }
    }

    override fun fileMoved(event: VirtualFileMoveEvent) {
      handleFileMovement(event.file)
    }

    private fun handleFileMovement(file: VirtualFile) {
      if (!partialChangeListsEnabled) return

      synchronized(LOCK) {
        forEachTrackerUnder(file) { data ->
          reregisterTrackerInCLM(data)
        }
      }
    }

    override fun fileDeleted(event: VirtualFileEvent) {
      if (!partialChangeListsEnabled) return

      synchronized(LOCK) {
        forEachTrackerUnder(event.file) { data ->
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
        val document = fileDocumentManager.getCachedDocument(file) ?: return
        val data = trackers[document] ?: return

        action(data)
      }
    }
  }

  private inner class MyDocumentListener : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      if (!ApplicationManager.getApplication().isDispatchThread) return // disable for documents forUseInNonAWTThread
      if (!partialChangeListsEnabled) return

      val document = event.document
      if (documentsInDefaultChangeList.contains(document)) return

      val virtualFile = fileDocumentManager.getFile(document) ?: return
      if (getLineStatusTracker(document) != null) return
      if (!canGetBaseRevisionFor(virtualFile)) return
      if (!canCreatePartialTrackerFor(virtualFile)) return

      val changeList = changeListManager.getChangeList(virtualFile)
      if (changeList != null && !changeList.isDefault) {
        log("Tracker install from DocumentListener: ", virtualFile)

        val tracker = doInstallTracker(virtualFile, document)
        if (tracker is ChangelistsLocalLineStatusTracker) {
          tracker.replayChangesFromDocumentEvents(listOf(event))
        }
        return
      }

      documentsInDefaultChangeList.add(document)
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
      partialChangeListsEnabled = VcsApplicationSettings.getInstance().ENABLE_PARTIAL_CHANGELISTS && Registry.`is`("vcs.enable.partial.changelists")

      updateTrackingModes()
    }
  }

  private inner class MyChangeListListener : ChangeListAdapter() {
    override fun defaultListChanged(oldDefaultList: ChangeList?, newDefaultList: ChangeList?) {
      runInEdt(ModalityState.any()) {
        expireInactiveRangesDamagedNotifications()

        EditorFactory.getInstance().allEditors
          .filterIsInstance(EditorEx::class.java)
          .forEach {
            it.gutterComponentEx.repaint()
          }
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
      return object : CheckinHandler() {
        override fun checkinSuccessful() {
          runInEdt {
            getInstanceImpl(panel.project).resetExcludedFromCommitMarkers()
          }
        }

        override fun checkinFailed(exception: MutableList<VcsException>?) {
          runInEdt {
            getInstanceImpl(panel.project).resetExcludedFromCommitMarkers()
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


  private fun shouldBeUpdated(oldInfo: ContentInfo?, newInfo: ContentInfo): Boolean {
    if (oldInfo == null) return true
    if (oldInfo.revision == newInfo.revision && oldInfo.revision != VcsRevisionNumber.NULL) {
      return oldInfo.charset != newInfo.charset
    }
    return true
  }

  private class TrackerData(val tracker: LocalLineStatusTracker<*>,
                            var contentInfo: ContentInfo? = null,
                            var clmFilePath: FilePath? = null)

  private class ContentInfo(val revision: VcsRevisionNumber, val charset: Charset)


  private class RefreshRequest(val document: Document) {
    override fun equals(other: Any?): Boolean = other is RefreshRequest && document == other.document
    override fun hashCode(): Int = document.hashCode()
    override fun toString(): String = "RefreshRequest: " + (FileDocumentManager.getInstance().getFile(document)?.path ?: "unknown")
  }

  private class RefreshData(val text: String, val info: ContentInfo)


  private fun log(message: String, file: VirtualFile?) {
    if (LOG.isDebugEnabled) {
      if (file != null) {
        LOG.debug(message + "; file: " + file.path)
      }
      else {
        LOG.debug(message)
      }
    }
  }

  private fun warn(message: String, document: Document?) {
    val file = document?.let { fileDocumentManager.getFile(it) }
    warn(message, file)
  }

  private fun warn(message: String, file: VirtualFile?) {
    if (file != null) {
      LOG.warn(message + "; file: " + file.path)
    }
    else {
      LOG.warn(message)
    }
  }


  @CalledInAwt
  fun resetExcludedFromCommitMarkers() {
    application.assertIsDispatchThread()
    synchronized(LOCK) {
      val documents = mutableListOf<Document>()

      for (data in trackers.values) {
        val tracker = data.tracker
        if (tracker is ChangelistsLocalLineStatusTracker) {
          tracker.setExcludedFromCommit(false)
          documents.add(tracker.document)
        }
      }

      for (document in documents) {
        checkIfTrackerCanBeReleased(document)
      }
    }
  }


  @CalledInAwt
  internal fun collectPartiallyChangedFilesStates(): List<ChangelistsLocalLineStatusTracker.FullState> {
    application.assertIsDispatchThread()
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

  @CalledInAwt
  internal fun restoreTrackersForPartiallyChangedFiles(trackerStates: List<ChangelistsLocalLineStatusTracker.State>) {
    runWriteAction {
      synchronized(LOCK) {
        for (state in trackerStates) {
          val virtualFile = state.virtualFile
          val document = fileDocumentManager.getDocument(virtualFile) ?: continue

          if (!canCreatePartialTrackerFor(virtualFile)) continue

          val oldData = trackers[document]
          val oldTracker = oldData?.tracker
          if (oldTracker is ChangelistsLocalLineStatusTracker) {
            val stateRestored = state is ChangelistsLocalLineStatusTracker.FullState &&
                                oldTracker.restoreState(state)
            if (stateRestored) {
              log("Tracker restore: reused, full restored", virtualFile)
            }
            else {
              val isLoading = loader.hasRequest(RefreshRequest(document))
              if (isLoading) {
                fileStatesAwaitingRefresh.put(state.virtualFile, state)
                log("Tracker restore: reused, restore scheduled", virtualFile)
              }
              else {
                oldTracker.restoreState(state)
                log("Tracker restore: reused, restored", virtualFile)
              }
            }
          }
          else {
            val tracker = ChangelistsLocalLineStatusTracker.createTracker(project, document, virtualFile, getTrackingMode())

            val data = TrackerData(tracker)
            trackers.put(document, data)

            if (oldTracker != null) {
              eventDispatcher.multicaster.onTrackerRemoved(tracker)
              unregisterTrackerInCLM(oldData)
              oldTracker.release()
              log("Tracker restore: removed existing", virtualFile)
            }

            registerTrackerInCLM(data)
            refreshTracker(tracker)
            eventDispatcher.multicaster.onTrackerAdded(tracker)

            val stateRestored = state is ChangelistsLocalLineStatusTracker.FullState &&
                                tracker.restoreState(state)
            if (stateRestored) {
              log("Tracker restore: created, full restored", virtualFile)
            }
            else {
              fileStatesAwaitingRefresh.put(state.virtualFile, state)
              log("Tracker restore: created, restore scheduled", virtualFile)
            }
          }
        }

        loader.addAfterUpdateRunnable(Runnable {
          synchronized(LOCK) {
            log("Tracker restore: finished", null)
            fileStatesAwaitingRefresh.clear()
          }
        })
      }
    }
  }


  @CalledInAwt
  internal fun notifyInactiveRangesDamaged(virtualFile: VirtualFile) {
    application.assertIsDispatchThread()
    if (filesWithDamagedInactiveRanges.contains(virtualFile)) return
    if (virtualFile == fileEditorManager.currentFile) return
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

  @CalledInAwt
  private fun expireInactiveRangesDamagedNotifications() {
    filesWithDamagedInactiveRanges.clear()

    val currentNotifications = NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(InactiveRangesDamagedNotification::class.java, project)
    currentNotifications.forEach { it.expire() }
  }

  private class InactiveRangesDamagedNotification(project: Project, val virtualFiles: Set<VirtualFile>)
    : Notification(VcsNotifier.STANDARD_NOTIFICATION.displayId,
                   AllIcons.Toolwindows.ToolWindowChanges,
                   null,
                   null,
                   VcsBundle.getString("lst.inactive.ranges.damaged.notification"),
                   NotificationType.INFORMATION,
                   null) {
    init {
      addAction(NotificationAction.createSimple("View Changes...") {
        val defaultList = ChangeListManager.getInstance(project).defaultChangeList
        val changes = defaultList.changes.filter { virtualFiles.contains(it.virtualFile) }

        val window = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID)
        window.activate { ChangesViewManager.getInstance(project).selectChanges(changes) }
        expire()
      })
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
        throw IllegalStateException("Couldn't await base contents")
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
private abstract class SingleThreadLoader<Request, T> {
  private val LOG = Logger.getInstance(SingleThreadLoader::class.java)
  private val LOCK: Any = Any()

  private val taskQueue = ArrayDeque<Request>()
  private val waitingForRefresh = HashSet<Request>()

  private val callbacksWaitingUpdateCompletion = ArrayList<Runnable>()

  private var isScheduled: Boolean = false
  private var isDisposed: Boolean = false
  private var lastFuture: Future<*>? = null

  @CalledInBackground
  protected abstract fun loadRequest(request: Request): Result<T>

  @CalledInAwt
  protected abstract fun handleResult(request: Request, result: Result<T>)


  @CalledInAwt
  fun scheduleRefresh(request: Request) {
    if (isDisposed) return

    synchronized(LOCK) {
      if (taskQueue.contains(request)) return
      taskQueue.add(request)

      schedule()
    }
  }

  @CalledInAwt
  fun dispose() {
    val callbacks = mutableListOf<Runnable>()
    synchronized(LOCK) {
      isDisposed = true
      taskQueue.clear()
      waitingForRefresh.clear()
      lastFuture?.cancel(true)

      callbacks += callbacksWaitingUpdateCompletion
      callbacksWaitingUpdateCompletion.clear()
    }

    executeCallbacks(callbacksWaitingUpdateCompletion)
  }

  @CalledInAwt
  fun hasRequest(request: Request): Boolean {
    synchronized(LOCK) {
      for (refreshData in taskQueue) {
        if (refreshData == request) return true
      }
      for (refreshData in waitingForRefresh) {
        if (refreshData == request) return true
      }
    }
    return false
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
    if (isDisposed) return

    synchronized(LOCK) {
      if (isScheduled) return
      if (taskQueue.isEmpty()) return

      isScheduled = true
      lastFuture = ApplicationManager.getApplication().executeOnPooledThread {
        handleRequests()
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
        notifyTrackerRefreshed(request)
      }
    }
  }

  @CalledInAwt
  private fun notifyTrackerRefreshed(request: Request) {
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

  @CalledInAwt
  private fun executeCallbacks(callbacks: List<Runnable>) {
    for (callback in callbacks) {
      try {
        callback.run()
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