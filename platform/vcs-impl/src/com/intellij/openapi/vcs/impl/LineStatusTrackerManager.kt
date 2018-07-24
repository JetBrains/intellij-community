/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.impl

import com.google.common.collect.HashMultiset
import com.google.common.collect.Multiset
import com.intellij.icons.AllIcons
import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryAdapter
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictFileStatusProvider
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.*
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.*
import java.nio.charset.Charset
import java.util.*

class LineStatusTrackerManager(
  private val project: Project,
  private val application: Application,
  private val statusProvider: VcsBaseContentProvider,
  private val changeListManager: ChangeListManagerImpl,
  private val fileDocumentManager: FileDocumentManager,
  private val fileEditorManager: FileEditorManagerEx,
  @Suppress("UNUSED_PARAMETER") makeSureIndexIsInitializedFirst: DirectoryIndex
) : ProjectComponent, LineStatusTrackerManagerI {

  private val LOCK = Any()
  private val disposable: Disposable = Disposer.newDisposable()
  private var isDisposed = false

  private val trackers = HashMap<Document, TrackerData>()
  private val forcedDocuments = HashMap<Document, Multiset<Any>>()

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  private var partialChangeListsEnabled = VcsApplicationSettings.getInstance().ENABLE_PARTIAL_CHANGELISTS && Registry.`is`("vcs.enable.partial.changelists")
  private val documentsInDefaultChangeList = HashSet<Document>()
  private var batchChangeTaskCounter: Int = 0

  private val filesWithDamagedInactiveRanges = HashSet<VirtualFile>()
  private val fileStatesAwaitingRefresh = HashMap<VirtualFile, PartialLocalLineStatusTracker.State>()

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

      application.addApplicationListener(MyApplicationListener(), disposable)

      val projectConnection = project.messageBus.connect(disposable)
      projectConnection.subscribe(LineStatusTrackerSettingListener.TOPIC, MyLineStatusTrackerSettingListener())

      val appConnection = application.messageBus.connect(disposable)
      appConnection.subscribe(BatchFileChangeListener.TOPIC, MyBatchFileChangeListener())

      val fsManager = FileStatusManager.getInstance(project)
      fsManager.addFileStatusListener(MyFileStatusListener(), disposable)

      val editorFactory = EditorFactory.getInstance()
      editorFactory.addEditorFactoryListener(MyEditorFactoryListener(), disposable)
      editorFactory.eventMulticaster.addDocumentListener(MyDocumentListener(), disposable)

      changeListManager.addChangeListListener(MyChangeListListener())

      val virtualFileManager = VirtualFileManager.getInstance()
      virtualFileManager.addVirtualFileListener(MyVirtualFileListener(), disposable)

      CommandProcessor.getInstance().addCommandListener(MyCommandListener(), disposable)
    }
  }

  override fun disposeComponent() {
    isDisposed = true
    Disposer.dispose(disposable)

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

  @NonNls
  override fun getComponentName(): String {
    return "LineStatusTrackerManager"
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

      if (data.tracker is PartialLocalLineStatusTracker) {
        val hasPartialChanges = data.tracker.affectedChangeListsIds.size > 1
        val hasBlocksExcludedFromCommit = data.tracker.hasBlocksExcludedFromCommit()
        val isLoading = loader.hasRequest(RefreshRequest(document))
        if (hasPartialChanges || hasBlocksExcludedFromCommit || isLoading) return
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
        val isPartialTracker = tracker is PartialLocalLineStatusTracker

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
    if (tracker !is PartialLocalLineStatusTracker) return

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
    if (tracker !is PartialLocalLineStatusTracker) return

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
    if (tracker !is PartialLocalLineStatusTracker) return

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
    if (virtualFile == null || virtualFile is LightVirtualFile || !virtualFile.isValid) return false
    if (virtualFile.fileType.isBinary || FileUtilRt.isTooLarge(virtualFile.length)) return false
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
    if (getTrackingMode() == LineStatusTracker.Mode.SILENT) return false

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
        PartialLocalLineStatusTracker.createTracker(project, document, virtualFile, getTrackingMode())
      }
      else {
        SimpleLocalLineStatusTracker.createTracker(project, document, virtualFile, getTrackingMode())
      }

      val data = TrackerData(tracker)
      trackers.put(document, data)

      registerTrackerInCLM(data)
      refreshTracker(tracker)
      eventDispatcher.multicaster.onTrackerAdded(tracker)

      if (batchChangeTaskCounter > 0) {
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
        val isPartialTracker = tracker is PartialLocalLineStatusTracker

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

  private fun getTrackingMode(): LineStatusTracker.Mode {
    val settings = VcsApplicationSettings.getInstance()
    if (!settings.SHOW_LST_GUTTER_MARKERS) return LineStatusTracker.Mode.SILENT
    if (settings.SHOW_WHITESPACES_IN_LST) return LineStatusTracker.Mode.SMART
    return LineStatusTracker.Mode.DEFAULT
  }

  @CalledInAwt
  private fun refreshTracker(tracker: LineStatusTracker<*>) {
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
      if (tracker is PartialLocalLineStatusTracker) {
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

      val tracker = getLineStatusTracker(document)!!
      tracker.setBaseRevision(refreshData.text)
      log("Loading finished: success", virtualFile)

      if (tracker is PartialLocalLineStatusTracker) {
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

  private inner class MyEditorFactoryListener : EditorFactoryAdapter() {
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
        if (file.isDirectory) {
          for (data in trackers.values) {
            if (VfsUtil.isAncestor(file, data.tracker.virtualFile, false)) {
              reregisterTrackerInCLM(data)
            }
          }
        }
        else {
          val document = fileDocumentManager.getCachedDocument(file) ?: return
          val data = trackers[document] ?: return

          reregisterTrackerInCLM(data)
        }
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
        if (tracker is PartialLocalLineStatusTracker) {
          tracker.replayChangesFromDocumentEvents(listOf(event))
        }
        return
      }

      documentsInDefaultChangeList.add(document)
    }
  }

  private inner class MyApplicationListener : ApplicationAdapter() {
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
    override fun commandFinished(event: CommandEvent?) {
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

  private inner class MyBatchFileChangeListener : BatchFileChangeListener {
    override fun batchChangeStarted(eventProject: Project, activityName: String?) {
      if (eventProject != project) return
      runReadAction {
        synchronized(LOCK) {
          if (batchChangeTaskCounter == 0) {
            for (data in trackers.values) {
              try {
                data.tracker.freeze()
              }
              catch (e: Throwable) {
                LOG.error(e)
              }
            }
          }
          batchChangeTaskCounter++
        }
      }
    }

    override fun batchChangeCompleted(eventProject: Project) {
      if (eventProject != project) return
      runInEdt(ModalityState.any()) {
        synchronized(LOCK) {
          batchChangeTaskCounter--
          if (batchChangeTaskCounter == 0) {
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

  private class TrackerData(val tracker: LineStatusTracker<*>,
                            var contentInfo: ContentInfo? = null,
                            var clmFilePath: FilePath? = null)

  private class ContentInfo(val revision: VcsRevisionNumber, val charset: Charset)


  private class RefreshRequest(val document: Document) {
    override fun equals(other: Any?): Boolean = other is RefreshRequest && document == other.document
    override fun hashCode(): Int = document.hashCode()
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
        if (tracker is PartialLocalLineStatusTracker) {
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
  internal fun collectPartiallyChangedFilesStates(): List<PartialLocalLineStatusTracker.FullState> {
    application.assertIsDispatchThread()
    val result = mutableListOf<PartialLocalLineStatusTracker.FullState>()
    synchronized(LOCK) {
      for (data in trackers.values) {
        val tracker = data.tracker
        if (tracker is PartialLocalLineStatusTracker) {
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
  internal fun restoreTrackersForPartiallyChangedFiles(trackerStates: List<PartialLocalLineStatusTracker.State>) {
    runWriteAction {
      synchronized(LOCK) {
        for (state in trackerStates) {
          val virtualFile = state.virtualFile
          val document = fileDocumentManager.getDocument(virtualFile) ?: continue

          if (!canCreatePartialTrackerFor(virtualFile)) continue

          val oldData = trackers[document]
          val oldTracker = oldData?.tracker
          if (oldTracker is PartialLocalLineStatusTracker) {
            val stateRestored = state is PartialLocalLineStatusTracker.FullState &&
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
            val tracker = PartialLocalLineStatusTracker.createTracker(project, document, virtualFile, getTrackingMode())

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

            val stateRestored = state is PartialLocalLineStatusTracker.FullState &&
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
      if (System.currentTimeMillis() - start > 2000) {
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

  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService("LineStatusTrackerManager Pool", 1)

  private val taskQueue = ArrayDeque<Request>()
  private val waitingForRefresh = HashSet<Request>()

  private val callbacksWaitingUpdateCompletion = ArrayList<Runnable>()

  private var isScheduled: Boolean = false
  private var isDisposed: Boolean = false


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
      executor.execute {
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
}

private sealed class Result<T> {
  class Success<T>(val data: T) : Result<T>()
  class Canceled<T> : Result<T>()
  class Error<T> : Result<T>()
}