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
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationAdapter
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryAdapter
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.SimpleLocalLineStatusTracker
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.*
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.GuiUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.HashMap
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.NonNls
import java.nio.charset.Charset
import java.util.*

class LineStatusTrackerManager(
  private val project: Project,
  private val application: Application,
  private val statusProvider: VcsBaseContentProvider,
  private val changeListManager: ChangeListManagerImpl,
  private val fileDocumentManager: FileDocumentManager,
  @Suppress("UNUSED_PARAMETER") makeSureIndexIsInitializedFirst: DirectoryIndex
) : ProjectComponent, LineStatusTrackerManagerI {

  private val LOCK = Any()
  private val disposable: Disposable = Disposer.newDisposable()
  private var isDisposed = false

  private val trackers = HashMap<Document, TrackerData>()
  private val forcedDocuments = HashMap<Document, Multiset<Any>>()

  private val partialChangeListsEnabled = Registry.`is`("vcs.enable.partial.changelists")
  private val documentsInDefaultChangeList = HashSet<Document>()

  private val loader: SingleThreadLoader<RefreshRequest, RefreshData> = MyBaseRevisionLoader()

  companion object {
    private val LOG = Logger.getInstance(LineStatusTrackerManager::class.java)

    @JvmStatic
    fun getInstance(project: Project): LineStatusTrackerManagerI {
      return project.getComponent(LineStatusTrackerManagerI::class.java)
    }
  }

  override fun initComponent() {
    StartupManager.getInstance(project).registerPreStartupActivity {
      if (isDisposed) return@registerPreStartupActivity

      application.addApplicationListener(MyApplicationListener(), disposable)

      val busConnection = project.messageBus.connect(disposable)
      busConnection.subscribe(LineStatusTrackerSettingListener.TOPIC, MyLineStatusTrackerSettingListener())

      val fsManager = FileStatusManager.getInstance(project)
      fsManager.addFileStatusListener(MyFileStatusListener(), disposable)

      val editorFactory = EditorFactory.getInstance()
      editorFactory.addEditorFactoryListener(MyEditorFactoryListener(), disposable)
      if (partialChangeListsEnabled) editorFactory.eventMulticaster.addDocumentListener(MyDocumentListener(), disposable)

      changeListManager.addChangeListListener(MyChangeListListener())

      val virtualFileManager = VirtualFileManager.getInstance()
      virtualFileManager.addVirtualFileListener(MyVirtualFileListener(), disposable)
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
        unregisterTrackerInCLM(data.tracker)
        data.tracker.release()
      }
      trackers.clear()

      loader.clear()
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


  @CalledInAwt
  private fun checkIfTrackerCanBeReleased(document: Document) {
    synchronized(LOCK) {
      val data = trackers[document] ?: return

      if (forcedDocuments.containsKey(document)) return

      if (data.tracker is PartialLocalLineStatusTracker) {
        val hasPartialChanges = data.tracker.getAffectedChangeListsIds().size > 1
        val isLoading = loader.hasRequest(RefreshRequest(document))
        if (hasPartialChanges || isLoading) return
      }

      releaseTracker(document)
    }
  }


  @CalledInAwt
  private fun onEverythingChanged() {
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

  private fun registerTrackerInCLM(tracker: LineStatusTracker<*>) {
    if (tracker is PartialLocalLineStatusTracker) {
      val filePath = VcsUtil.getFilePath(tracker.virtualFile)
      changeListManager.registerChangeTracker(filePath, tracker)
    }
  }

  private fun unregisterTrackerInCLM(tracker: LineStatusTracker<*>) {
    if (tracker is PartialLocalLineStatusTracker) {
      val filePath = VcsUtil.getFilePath(tracker.virtualFile)
      changeListManager.unregisterChangeTracker(filePath, tracker)
    }
  }

  private fun reregisterTrackerInCLM(tracker: LineStatusTracker<*>, oldPath: FilePath, newPath: FilePath) {
    if (tracker is PartialLocalLineStatusTracker) {
      changeListManager.unregisterChangeTracker(oldPath, tracker)
      changeListManager.registerChangeTracker(newPath, tracker)
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
        status != FileStatus.NOT_CHANGED) return false

    val change = ChangeListManager.getInstance(project).getChange(virtualFile)
    return change != null && change.javaClass == Change::class.java &&
           change.type == Change.Type.MODIFICATION && change.afterRevision is CurrentContentRevision

  }

  override fun arePartialChangelistsEnabled(virtualFile: VirtualFile): Boolean {
    if (!partialChangeListsEnabled) return false
    if (getTrackingMode() == LineStatusTracker.Mode.SILENT) return false

    val vcs = VcsUtil.getVcsFor(project, virtualFile)
    return vcs != null && vcs.arePartialChangelistsSupported()
  }


  @CalledInAwt
  private fun installTracker(virtualFile: VirtualFile,
                             document: Document) {
    if (!canGetBaseRevisionFor(virtualFile)) return

    val changelistId = changeListManager.getChangeList(virtualFile)?.id
    installTracker(virtualFile, document, changelistId, emptyList())
  }

  @CalledInAwt
  private fun installTracker(virtualFile: VirtualFile,
                             document: Document,
                             oldChangesChangelistId: String?,
                             events: List<DocumentEvent>) {
    synchronized(LOCK) {
      if (isDisposed) return
      if (trackers[document] != null) return

      val tracker = if (canCreatePartialTrackerFor(virtualFile)) {
        PartialLocalLineStatusTracker.createTracker(project, document, virtualFile, getTrackingMode(), events)
      }
      else {
        SimpleLocalLineStatusTracker.createTracker(project, document, virtualFile, getTrackingMode())
      }

      trackers.put(document, TrackerData(tracker))

      registerTrackerInCLM(tracker)
      refreshTracker(tracker, oldChangesChangelistId)

      log("Tracker installed", virtualFile)
    }
  }

  @CalledInAwt
  private fun releaseTracker(document: Document) {
    synchronized(LOCK) {
      if (isDisposed) return
      val data = trackers.remove(document) ?: return

      unregisterTrackerInCLM(data.tracker)
      data.tracker.release()

      log("Tracker released", data.tracker.virtualFile)
    }
  }

  private fun getTrackingMode(): LineStatusTracker.Mode {
    val settings = VcsApplicationSettings.getInstance()
    if (!settings.SHOW_LST_GUTTER_MARKERS) return LineStatusTracker.Mode.SILENT
    if (settings.SHOW_WHITESPACES_IN_LST) return LineStatusTracker.Mode.SMART
    return LineStatusTracker.Mode.DEFAULT
  }

  @CalledInAwt
  private fun refreshTracker(tracker: LineStatusTracker<*>, changelistId: String? = null) {
    synchronized(LOCK) {
      if (isDisposed) return
      loader.scheduleRefresh(RefreshRequest(tracker.document, changelistId))

      log("Refresh queued", tracker.virtualFile)
    }
  }

  private inner class MyBaseRevisionLoader() : SingleThreadLoader<RefreshRequest, RefreshData>(project) {
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

    override fun handleResult(request: RefreshRequest, result: Result<RefreshData>) {
      val document = request.document
      when (result) {
        is Result.Canceled -> {
        }
        is Result.Error -> {
          edt {
            synchronized(LOCK) {
              val data = trackers[document] ?: return@edt

              data.tracker.dropBaseRevision()
              data.contentInfo = null

              checkIfTrackerCanBeReleased(document)
            }
          }
        }
        is Result.Success -> {
          edt {
            val virtualFile = fileDocumentManager.getFile(document)!!
            val refreshData = result.data

            synchronized(LOCK) {
              val data = trackers[document]
              if (data == null) {
                log("Loading finished: tracker already released", virtualFile)
                return@edt
              }
              if (!shouldBeUpdated(data.contentInfo, refreshData.info)) {
                log("Loading finished: no need to update", virtualFile)
                return@edt
              }

              data.contentInfo = refreshData.info
              if (data.tracker is PartialLocalLineStatusTracker) {
                val changelist = request.changelistId ?: changeListManager.getChangeList(virtualFile)?.id
                data.tracker.setBaseRevision(refreshData.text, changelist)
              }
              else {
                data.tracker.setBaseRevision(refreshData.text)
              }
              log("Loading finished: success", virtualFile)
            }
          }
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
      return editor.project == null || editor.project == project
    }
  }

  private inner class MyVirtualFileListener : VirtualFileListener {
    override fun beforePropertyChange(event: VirtualFilePropertyEvent) {
      if (VirtualFile.PROP_ENCODING == event.propertyName) {
        onFileChanged(event.file)
      }
      if (VirtualFile.PROP_NAME == event.propertyName) {
        val file = event.file
        val parent = event.parent
        if (parent != null) {
          handleFileMovement(file) {
            Pair(VcsUtil.getFilePath(parent, event.oldValue as String),
                 VcsUtil.getFilePath(parent, event.newValue as String))
          }
        }
      }
    }

    override fun beforeFileMovement(event: VirtualFileMoveEvent) {
      val file = event.file
      handleFileMovement(file) {
        Pair(VcsUtil.getFilePath(event.oldParent, file.name),
             VcsUtil.getFilePath(event.newParent, file.name))
      }
    }

    private fun handleFileMovement(file: VirtualFile, getPaths: () -> Pair<FilePath, FilePath>) {
      if (!partialChangeListsEnabled) return

      synchronized(LOCK) {
        val tracker = getLineStatusTracker(file)
        if (tracker != null) {
          val (oldPath, newPath) = getPaths()
          reregisterTrackerInCLM(tracker, oldPath, newPath)
        }
      }
    }
  }

  private inner class MyDocumentListener : DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
      val document = event.document
      if (documentsInDefaultChangeList.contains(document)) return

      val virtualFile = fileDocumentManager.getFile(document) ?: return
      if (getLineStatusTracker(document) != null) return
      if (!canCreatePartialTrackerFor(virtualFile)) return

      val changeList = changeListManager.getChangeList(virtualFile)
      if (changeList != null && !changeList.isDefault) {
        installTracker(virtualFile, document, changeList.id, listOf(event))
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
      synchronized(LOCK) {
        val mode = getTrackingMode()
        for (data in trackers.values) {
          val tracker = data.tracker
          val document = tracker.document
          val virtualFile = tracker.virtualFile

          if (tracker.mode == mode) continue

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
  }

  private inner class MyChangeListListener : ChangeListAdapter() {
    override fun defaultListChanged(oldDefaultList: ChangeList?, newDefaultList: ChangeList?) {
      edt {
        EditorFactory.getInstance().allEditors
          .filterIsInstance(EditorEx::class.java)
          .forEach {
            it.gutterComponentEx.repaint()
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
                            var contentInfo: ContentInfo? = null)

  private class ContentInfo(val revision: VcsRevisionNumber, val charset: Charset)


  private class RefreshRequest(val document: Document, val changelistId: String? = null) {
    override fun equals(other: Any?): Boolean = other is RefreshRequest && document == other.document
    override fun hashCode(): Int = document.hashCode()
  }

  private class RefreshData(val text: String, val info: ContentInfo)


  private fun edt(task: () -> Unit) {
    GuiUtils.invokeLaterIfNeeded(task, ModalityState.any())
  }

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
  internal fun collectPartiallyChangedFilesStates(): List<PartialLocalLineStatusTracker.State> {
    val result = mutableListOf<PartialLocalLineStatusTracker.State>()
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
    synchronized(LOCK) {
      for (state in trackerStates) {
        val virtualFile = state.virtualFile
        val document = fileDocumentManager.getDocument(virtualFile) ?: continue

        if (!canCreatePartialTrackerFor(virtualFile)) continue

        val tracker = PartialLocalLineStatusTracker.createTracker(project, document, virtualFile, getTrackingMode(), state)
        val oldTracker = trackers.put(document, TrackerData(tracker))

        if (oldTracker != null) {
          unregisterTrackerInCLM(oldTracker.tracker)
          oldTracker.tracker.release()
        }

        registerTrackerInCLM(tracker)
        refreshTracker(tracker)

        log("Tracker restored from config", virtualFile)
      }
    }
  }
}


/**
 * Single threaded queue with the following properties:
 * - Ignores duplicated requests (the first queued is used).
 * - Allows to check whether request is scheduled or is waiting for completion.
 * - Notifies callbacks when queue is exhausted.
 */
abstract private class SingleThreadLoader<Request, T>(private val project: Project) {
  private val LOG = Logger.getInstance(SingleThreadLoader::class.java)
  private val LOCK: Any = Any()

  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService("LineStatusTrackerManager pool", 1)

  private val taskQueue = ArrayDeque<Request>()
  private val waitingForRefresh = HashSet<Request>()

  private val callbacksWaitingUpdateCompletion = ArrayList<Runnable>()

  private var isScheduled: Boolean = false


  protected abstract fun loadRequest(request: Request): Result<T>
  protected abstract fun handleResult(request: Request, result: Result<T>)


  @CalledInAwt
  fun scheduleRefresh(request: Request) {
    if (isDisposed()) return

    synchronized(LOCK) {
      if (taskQueue.contains(request)) return
      taskQueue.add(request)

      schedule()
    }
  }

  @CalledInAwt
  fun clear() {
    val callbacks = mutableListOf<Runnable>()
    synchronized(LOCK) {
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

    edt {
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
    if (isDisposed()) return

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

        if (isDisposed() || request == null) {
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

    edt {
      handleResult(request, result)
      notifyTrackerRefreshed(request)
    }
  }

  @CalledInAwt
  private fun notifyTrackerRefreshed(request: Request) {
    if (isDisposed()) return

    val callbacks = mutableListOf<Runnable>()
    synchronized(LOCK) {
      waitingForRefresh.remove(request)

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

  private fun isDisposed() = project.isDisposed

  private fun edt(task: () -> Unit) {
    GuiUtils.invokeLaterIfNeeded(task, ModalityState.any())
  }
}

private sealed class Result<T> {
  class Success<T>(val data: T) : Result<T>()
  class Canceled<T> : Result<T>()
  class Error<T> : Result<T>()
}