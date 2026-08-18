// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.CommonBundle
import com.intellij.concurrency.SensitiveProgressWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.rethrowControlFlowException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsConnectionProblem
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.changes.ChangeListWorker.ChangeListUpdater
import com.intellij.openapi.vcs.changes.ChangeListWorker.PartialChangeTracker
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder.VcsManagedFilesHolderListener
import com.intellij.openapi.vcs.changes.actions.ChangeListRemoveConfirmation
import com.intellij.openapi.vcs.changes.actions.ScheduleForAdditionAction
import com.intellij.openapi.vcs.changes.actions.VcsStatisticsCollector
import com.intellij.openapi.vcs.changes.conflicts.ChangelistConflictTracker
import com.intellij.openapi.vcs.changes.ui.ChangeListDeltaListener
import com.intellij.openapi.vcs.impl.AbstractVcsHelperImpl
import com.intellij.openapi.vcs.impl.VcsEP
import com.intellij.openapi.vcs.impl.VcsInitObject
import com.intellij.openapi.vcs.impl.VcsRootIterator
import com.intellij.openapi.vcs.impl.VcsStartupActivity
import com.intellij.openapi.vcs.readOnlyHandler.ReadonlyStatusHandlerImpl
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.changes.ChangeListManagerState
import com.intellij.platform.vcs.changes.ChangeListManagerState.FileHoldersState
import com.intellij.util.EventDispatcher
import com.intellij.util.SlowOperations
import com.intellij.util.ThreeState
import com.intellij.util.application
import com.intellij.util.asSafely
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.MultiMap
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.VcsConfirmationUtil
import com.intellij.vcs.commit.ChangeListCommitState
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.commit.ShowNotificationCommitResultHandler
import com.intellij.vcs.commit.SingleChangeListCommitter.Companion.create
import com.intellij.vcsUtil.VcsUtil
import com.intellij.xml.util.XmlStringUtil
import kotlinx.coroutines.CoroutineScope
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.io.File
import java.util.concurrent.CountDownLatch

private val LOG = logger<ChangeListManagerImpl>()
private const val DEADLOCK_ADVICE =
  "A lock may not be taken while ChangeListManagerImpl.dataLock is held, as this might lead to a deadlock"

@ApiStatus.Internal
@State(name = "ChangeListManager", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class ChangeListManagerImpl(
  private val project: Project,
  coroutineScope: CoroutineScope,
) : ChangeListManagerEx(),
    PersistentStateComponent<Element>,
    Disposable {
  private val scheduler = ChangeListScheduler(coroutineScope)
  private val updateDisposable = Disposer.newDisposable()

  private val listeners = EventDispatcher.create(ChangeListListener::class.java)

  /**
   * Notifies [listeners] on [scheduler]
   */
  private val delayedNotificator = DelayedNotificator(project, this, scheduler)

  private val worker = ChangeListWorker(project, delayedNotificator)

  private val updateRequestsQueue = UpdateRequestsQueue(project, scheduler, ::updateImmediately, ::hasNothingToUpdate)
  private val modifier = Modifier(worker, delayedNotificator)

  private val dataLock = Any()

  private var filesHolder = FileHolderComposite.create(project)

  private var disabledWorkerState: List<LocalChangeListImpl>? = null

  private var initialUpdate = true
  private var _updateException: VcsException? = null

  @Volatile
  private var showLocalChangesInvalidated = false

  private val stateProvider = ChangesListManagerStateProviderImpl.getInstance(project)

  private val listsToBeDeletedSilently = HashSet<String>()
  private val listsToBeDeleted = HashSet<String>()
  private var emptyListDeletionScheduled = false

  private var modalNotificationsBlocked = false

  val conflictTracker: ChangelistConflictTracker = ChangelistConflictTracker(project, this)

  init {
    val busConnection = project.getMessageBus().connect(coroutineScope)
    busConnection.subscribe(ChangeListListener.TOPIC, listeners.getMulticaster())
    listeners.addListener(object : ChangeListAdapter() {
      override fun defaultListChanged(oldDefaultList: ChangeList?, newDefaultList: ChangeList?, automatic: Boolean) {
        if (automatic || oldDefaultList == null || oldDefaultList == newDefaultList || oldDefaultList !is LocalChangeList) {
          return
        }
        scheduleAutomaticEmptyChangeListDeletion(oldDefaultList)
      }
    })

    busConnection.subscribe(VcsManagedFilesHolder.TOPIC,
                            VcsManagedFilesHolderListener {
                              val ignoredInUpdateMode = filesHolder.ignoredFileHolder.isInUpdatingMode
                              val unversionedInUpdateMode = filesHolder.unversionedFileHolder.isInUpdatingMode
                              stateProvider.setFileHolderState(FileHoldersState(unversionedInUpdateMode, ignoredInUpdateMode))
                            })

    VcsManagedFilesHolder.VCS_IGNORED_FILES_HOLDER_EP.addChangeListener(project, Runnable {
      VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    }, this)
    VcsEP.EP_NAME.addChangeListener(coroutineScope, Runnable {
      resetChangedFiles()
      VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    })

    CommitModeManager.subscribeOnCommitModeChange(busConnection, CommitModeManager.CommitModeListener { updateChangeListAvailability() })
    Registry.get("vcs.disable.changelists").addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        updateChangeListAvailability()
      }
    }, this)

    Disposer.register(this, updateDisposable) // register defensively, in case "projectClosing" won't be called
    busConnection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        if (project === this@ChangeListManagerImpl.project) {
          if (application.isUnitTestMode()) {
            @Suppress("TestOnlyProblems")
            waitEverythingDoneInTestMode()
          }
          // Can't use Project disposable - it will be called after pending tasks are finished
          Disposer.dispose(updateDisposable)
        }
      }
    })
  }

  override fun dispose() {
    updateRequestsQueue.stop()
  }

  override fun scheduleAutomaticEmptyChangeListDeletion(list: LocalChangeList) {
    scheduleAutomaticEmptyChangeListDeletion(list, false)
  }

  override fun scheduleAutomaticEmptyChangeListDeletion(oldList: LocalChangeList, silently: Boolean) {
    if (!silently && oldList.hasDefaultName()) return
    synchronized(dataLock) {
      LOG.debug { "Schedule empty changelist deletion: ${oldList.getName()}, silently = $silently" }
      if (silently) {
        listsToBeDeletedSilently.add(oldList.id)
      }
      else {
        listsToBeDeleted.add(oldList.id)
      }

      if (!emptyListDeletionScheduled) {
        emptyListDeletionScheduled = true
        invokeAfterUpdate(true) {
          deleteEmptyChangeLists()
        }
      }
    }
  }

  @RequiresEdt
  private fun deleteEmptyChangeLists() {
    val config = VcsConfiguration.getInstance(project)

    val toBeDeletedSilently: List<LocalChangeList>
    val toBeDeleted: List<LocalChangeList>

    val toDeleteMapping = { id: String? ->
      getChangeList(id)?.takeIf {
        !it.isDefault && !it.isReadOnly && it.changes.isEmpty()
      }
    }

    synchronized(dataLock) {
      LOG.debug {
        "Empty changelist deletion, scheduled:\nsilently: ${listsToBeDeletedSilently}\nasking: ${listsToBeDeleted}"
      }

      listsToBeDeleted.removeAll(listsToBeDeletedSilently)

      toBeDeletedSilently = listsToBeDeletedSilently.mapNotNullAndClear(toDeleteMapping)

      val askLater = modalNotificationsBlocked &&
                     config.REMOVE_EMPTY_INACTIVE_CHANGELISTS == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION
      val dontDelete = config.REMOVE_EMPTY_INACTIVE_CHANGELISTS == VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY ||
                       config.REMOVE_EMPTY_INACTIVE_CHANGELISTS == VcsShowConfirmationOption.Value.SHOW_CONFIRMATION && application.isUnitTestMode()
      toBeDeleted = when {
        askLater -> listOf()
        dontDelete -> {
          listsToBeDeleted.clear()
          listOf()
        }
        else -> listsToBeDeleted.mapNotNullAndClear(toDeleteMapping)
      }

      emptyListDeletionScheduled = false
      LOG.debug {
        "Empty changelist deletion, to be deleted:\nsilently: $toBeDeletedSilently\nasking: $toBeDeleted"
      }
    }

    ChangeListRemoveConfirmation.deleteEmptyInactiveLists(project, toBeDeletedSilently) { _ -> true }

    ChangeListRemoveConfirmation.deleteEmptyInactiveLists(project, toBeDeleted) { toAsk: List<LocalChangeList> ->
      config.REMOVE_EMPTY_INACTIVE_CHANGELISTS == VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY ||
      showRemoveEmptyChangeListsProposal(project, config, toAsk)
    }
  }

  @RequiresEdt
  override fun blockModalNotifications() {
    modalNotificationsBlocked = true
  }

  @RequiresEdt
  override fun unblockModalNotifications() {
    modalNotificationsBlocked = false
    deleteEmptyChangeLists()
  }

  private fun startUpdater() {
    updateRequestsQueue.initialized()
    project.getMessageBus().syncPublisher(LISTS_LOADED_TOPIC).processLoadedLists(getChangeLists())

    val connection = project.getMessageBus().connect(this)
    connection.subscribe(
      ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
      VcsMappingListener { VcsDirtyScopeManager.getInstance(project).markEverythingDirty() })
    if (!application.isUnitTestMode()) {
      conflictTracker.startTracking()
    }
  }

  fun registerChangeTracker(filePath: FilePath, tracker: PartialChangeTracker) {
    synchronized(dataLock) {
      worker.registerChangeTracker(filePath, tracker)
    }
  }

  fun unregisterChangeTracker(filePath: FilePath, tracker: PartialChangeTracker) {
    synchronized(dataLock) {
      worker.unregisterChangeTracker(filePath, tracker)
    }
  }

  /**
   * update itself might produce actions done on AWT thread (invoked-after),
   * so waiting for its completion on AWT thread is not good runnable is invoked on AWT thread
   */
  override fun invokeAfterUpdate(afterUpdate: Runnable, mode: InvokeAfterUpdateMode, title: String?, state: ModalityState?) {
    updateRequestsQueue.invokeAfterUpdate(afterUpdate, mode, title)
  }

  override fun freeze(reason: String) {
    if (!application.isHeadlessEnvironment()) {
      application.assertIsNonDispatchThread()
    }

    updateRequestsQueue.setIgnoreBackgroundOperation(true)
    val sem = Semaphore(1)

    invokeAfterUpdate(false, Runnable {
      updateRequestsQueue.setIgnoreBackgroundOperation(false)
      updateRequestsQueue.pause()
      stateProvider.setFreezeReason(reason)
      sem.up()
    })

    ProgressIndicatorUtils.awaitWithCheckCanceled(sem, ProgressManager.getInstance().getProgressIndicator())
  }

  override fun unfreeze() {
    updateRequestsQueue.go()
    stateProvider.setFreezeReason(null)
  }

  override fun waitForUpdate() {
    assert(!application.isReadAccessAllowed())
    val waiter = CountDownLatch(1)
    invokeAfterUpdate(false, Runnable { waiter.countDown() })
    ProgressIndicatorUtils.awaitWithCheckCanceled(waiter)
  }

  override fun promiseWaitForUpdate(): Promise<*> {
    val promise = AsyncPromise<Boolean?>()
    invokeAfterUpdate(false, Runnable { promise.setResult(true) })
    return promise
  }

  override fun isFreezed(): String? = stateProvider.state.value.asSafely<ChangeListManagerState.Frozen>()?.reason

  override fun isFreezedWithNotification(modalTitle: @Nls String?): Boolean {
    val freezeReason = isFreezed() ?: return false

    if (modalTitle != null) {
      Messages.showErrorDialog(project, freezeReason, modalTitle)
    }
    else {
      VcsBalloonProblemNotifier.showOverChangesView(project, freezeReason, MessageType.WARNING)
    }
    return true
  }

  fun executeOnUpdaterThread(operation: () -> Unit) {
    scheduler.submit(operation)
  }

  fun executeUnderDataLock(operation: () -> Unit) {
    runReadActionBlocking {
      synchronized(dataLock) {
        operation()
      }
    }
  }

  fun scheduleUpdateImpl() {
    updateRequestsQueue.schedule()
  }

  private fun resetChangedFiles() {
    try {
      synchronized(dataLock) {
        val dataHolder = DataHolder(filesHolder.copy(), ChangeListUpdater(worker), true).apply {
          notifyStart()
          notifyEnd()
          finish()
        }
        worker.applyChangesFromUpdate(dataHolder.updatedWorker, ChangesDeltaForwarder(project, scheduler))
        filesHolder = dataHolder.composite
        _updateException = null
      }

      // can be done with delay if plugin unloader can handle that - have to check
      project.getMessageBus().syncPublisher(ChangeListListener.TOPIC).changeListsInvalidated()
      delayedNotificator.changedFileStatusChanged(true)
      delayedNotificator.unchangedFileStatusChanged(true)
      delayedNotificator.changeListUpdateDone()
    }
    catch (ex: Exception) {
      LOG.error(ex)
    }
    catch (ex: AssertionError) {
      LOG.error(ex)
    }
  }

  /**
   * @return true if [updateImmediately] can be skipped.
   */
  private fun hasNothingToUpdate(): Boolean {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    if (!vcsManager.hasActiveVcss()) return true

    val dirtyScopeManager = VcsDirtyScopeManagerImpl.getInstanceImpl(project)
    return !dirtyScopeManager.hasDirtyScopes()
  }

  /**
   * @return false if update was re-scheduled due to new 'markEverythingDirty' event, true otherwise.
   */
  private fun updateImmediately() = BackgroundTaskUtil.runUnderDisposeAwareIndicator(updateDisposable, ::doUpdate)

  private fun doUpdate(): Boolean {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    if (!vcsManager.hasActiveVcss()) return true

    val dirtyScopeManager = VcsDirtyScopeManagerImpl.getInstanceImpl(project)
    val invalidated = dirtyScopeManager.retrieveScopes()
    if (invalidated == null || (!invalidated.isEverythingDirty && invalidated.isEmpty())) {
      LOG.debug("[update] - dirty scope is empty")
      dirtyScopeManager.changesProcessed()
      return true
    }

    try {
      if (updateRequestsQueue.isStopped) return true

      val wasEverythingDirty = invalidated.isEverythingDirty
      val scopes = invalidated.scopes
      // copy existing data to objects that would be updated.
      // mark for "modifier" that update started (it would create duplicates of modification commands done by user during update;
      // after update of copies of objects is complete, it would apply the same modifications to copies.)
      val newDataHolder: DataHolder
      val isInitialUpdate: Boolean
      synchronized(dataLock) {
        newDataHolder = DataHolder(filesHolder.copy(), ChangeListUpdater(worker), wasEverythingDirty)
        modifier.enterUpdate()
        stateProvider.setInUpdateMode(true)
        if (wasEverythingDirty) {
          _updateException = null
        }

        LOG.debug {
          val scope = scopes.joinToString(separator = "->\n") {
            it.toString()
          }
          val ignoredFilesCount = filesHolder.ignoredFileHolder.getFiles().size
          val unversionedFilesCount = filesHolder.unversionedFileHolder.getFiles().size

          "refresh procedure started, everything: $wasEverythingDirty dirty scope: $scope\n" +
          "ignored: $ignoredFilesCount\n" +
          "unversioned: $unversionedFilesCount\n" +
          "current changes: $worker"
        }

        isInitialUpdate = initialUpdate
        initialUpdate = false
      }
      // already on scheduler thread, so can just do a sync call
      project.getMessageBus().syncPublisher(ChangeListListener.TOPIC).changeListUpdateRunning()

      val vcsIndicator = SensitiveProgressWrapper(ProgressManager.getInstance().getProgressIndicator())
      if (!isInitialUpdate) {
        invalidated.doWhenCanceled(Runnable { vcsIndicator.cancel() })
      }

      try {
        ProgressManager.getInstance().executeProcessUnderProgress(Runnable {
          iterateScopes(newDataHolder, scopes, vcsIndicator)
        }, vcsIndicator)
      }
      catch (@Suppress("IncorrectCancellationExceptionHandling") _: ProcessCanceledException) {
      }
      val wasCancelled = vcsIndicator.isCanceled()

      // for the case of project being closed we need a read action here -> to be more consistent
      runReadActionBlocking {
        if (project.isDisposed()) return@runReadActionBlocking

        synchronized(dataLock) {
          val updatedWorker = newDataHolder.updatedWorker
          val takeChanges =
            _updateException == null
            && !wasCancelled
            && updatedWorker.areChangeListsEnabled() == worker.areChangeListsEnabled()

          // update member from copy
          if (takeChanges) {
            newDataHolder.finish()
            // do same modifications to change lists as was done during update + do delayed notifications
            modifier.finishUpdate(updatedWorker)

            worker.applyChangesFromUpdate(updatedWorker, ChangesDeltaForwarder(project, scheduler))

            LOG.debug {
              val unversionedFilesCount = newDataHolder.composite.unversionedFileHolder.getFiles().size
              "refresh procedure finished, unversioned size: $unversionedFilesCount\n" +
              "changes: $worker"
            }

            val statusChanged = filesHolder != newDataHolder.composite
            filesHolder = newDataHolder.composite
            if (statusChanged) {
              val isUnchangedUpdating = isInUpdate || isUnversionedInUpdateMode || isIgnoredInUpdateMode
              delayedNotificator.unchangedFileStatusChanged(!isUnchangedUpdating)
            }
            LOG.debug("[update] - success")
          }
          else {
            modifier.finishUpdate(null)
            LOG.debug { "[update] - aborted, wasCancelled: $wasCancelled" }
          }
          showLocalChangesInvalidated = false
        }
      }

      for (scope in scopes) {
        if (scope.getVcs().isTrackingUnchangedContent) {
          VcsRootIterator.iterateExistingInsideScope(scope) { file ->
            //todo: what if it has become dirty again during update?
            LastUnchangedContentTracker.markUntouched(file)
            true
          }
        }
      }

      return !wasCancelled
    }
    catch (@Suppress("IncorrectCancellationExceptionHandling") _: ProcessCanceledException) {
      // OK, we're finishing all the stuff now.
    }
    catch (ex: Exception) {
      LOG.error(ex)
    }
    catch (ex: AssertionError) {
      LOG.error(ex)
    }
    finally {
      dirtyScopeManager.changesProcessed()

      delayedNotificator.changedFileStatusChanged(!isInUpdate)
      delayedNotificator.changeListUpdateDone()

      stateProvider.setInUpdateMode(false)
    }
    return true
  }

  private fun iterateScopes(dataHolder: DataHolder, scopes: List<VcsModifiableDirtyScope>, indicator: ProgressIndicator) {
    val updater = dataHolder.changeListUpdater
    val composite = dataHolder.composite

    dataHolder.notifyStart()
    try {
      for (scope in scopes) {
        indicator.checkCanceled()

        // do actual requests about file statuses
        val builder = UpdatingChangeListBuilder(scope, updater, composite) {
          project.isDisposed() || updateRequestsQueue.isStopped
        }
        dataHolder.notifyStartProcessingChanges(scope)

        try {
          val vcs = scope.getVcs()
          val changeProvider = vcs.getChangeProvider()
          if (changeProvider != null) {
            val activity = VcsStatisticsCollector.logClmRefresh(project, vcs, scope.wasEveryThingDirty())
            changeProvider.getChanges(scope, builder, indicator, updater)
            activity.finished()
          }
        }
        catch (e: VcsException) {
          handleUpdateException(e)
        }
        catch (t: Throwable) {
          rethrowControlFlowException(t)
          LOG.debug(t)
          throw t
        }
        finally {
          if (!updateRequestsQueue.isStopped) {
            dataHolder.notifyDoneProcessingChanges(scope)
          }
        }

        synchronized(dataLock) {
          if (_updateException != null) break
        }
      }
    }
    finally {
      dataHolder.notifyEnd()
    }
  }

  private fun handleUpdateException(e: VcsException) {
    LOG.info(e)

    if (e is VcsConnectionProblem) {
      application.invokeLater { e.attemptQuickFix(false) }
    }

    if (application.isUnitTestMode()) {
      val helper = AbstractVcsHelper.getInstance(project)
      if (helper is AbstractVcsHelperImpl && helper.handleCustom(e)) {
        return
      }
      e.printStackTrace()
    }

    synchronized(dataLock) {
      _updateException = e
    }
  }

  override fun getChangeLists(): List<LocalChangeList> =
    synchronized(dataLock) {
      worker.getChangeLists()
    }

  @Suppress("IO_FILE_USAGE")
  override fun getAffectedPaths(): List<File> =
    synchronized(dataLock) {
      worker.getAffectedPaths()
    }.mapNotNull {
      it.ioFile
    }

  override fun getAffectedFiles(): List<VirtualFile> =
    synchronized(dataLock) {
      worker.getAffectedPaths()
    }.mapNotNull {
      it.virtualFile
    }

  override fun getAllChanges(): Collection<Change> =
    synchronized(dataLock) {
      worker.getAllChanges()
    }

  override fun getUnversionedFilesPaths(): List<FilePath> =
    runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.unversionedFileHolder.getFiles().toList()
      }
    }

  override fun isResolvedConflict(file: FilePath): Boolean {
    val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(file) ?: return false
    return runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.resolvedMergeFilesHolder.containsFile(file, vcsRoot)
      }
    }
  }

  override fun getResolvedConflictPaths(): List<FilePath> =
    runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.resolvedMergeFilesHolder.getFiles().toList()
      }
    }

  override fun getModifiedWithoutEditing(): List<VirtualFile> =
    runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.modifiedWithoutEditingFileHolder.files
      }
    }

  override fun getIgnoredFilePaths(): List<FilePath> =
    runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.ignoredFileHolder.getFiles().toList()
      }
    }

  val isUnversionedInUpdateMode: Boolean
    get() = runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.unversionedFileHolder.isInUpdatingMode()
      }
    }

  val isIgnoredInUpdateMode: Boolean
    get() = runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.ignoredFileHolder.isInUpdatingMode()
      }
    }

  val lockedFolders: List<VirtualFile>
    get() = runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.lockedFileHolder.files
      }
    }

  val logicallyLockedFolders: Map<VirtualFile, LogicalLock>
    get() = runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.logicallyLockedFileHolder.map.toMap()
      }
    }

  fun isLogicallyLocked(file: VirtualFile): Boolean =
    runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.logicallyLockedFileHolder.containsKey(file)
      }
    }

  fun isContainedInLocallyDeleted(filePath: FilePath): Boolean =
    runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.deletedFileHolder.isContainedInLocallyDeleted(filePath)
      }
    }

  val deletedFiles: List<LocallyDeletedChange>
    get() = runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.deletedFileHolder.getFiles()
      }
    }

  val switchedFilesMap: MultiMap<String, VirtualFile>
    get() = runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.switchedFileHolder.getBranchToFileMap()
      }
    }

  val switchedRoots: MutableMap<VirtualFile, String>
    get() = runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.rootSwitchFileHolder.getFilesMapCopy()
      }
    }

  override fun getUpdateException(): VcsException? =
    synchronized(dataLock) {
      _updateException
    }

  override fun isFileAffected(file: VirtualFile): Boolean {
    if (!file.isInLocalFileSystem) return false
    synchronized(dataLock) {
      return worker.getStatus(file) != null
    }
  }

  override fun findChangeList(name: String?): LocalChangeList? =
    synchronized(dataLock) {
      worker.getChangeListByName(name)
    }

  override fun getChangeList(id: String?): LocalChangeList? =
    synchronized(dataLock) {
      worker.getChangeListById(id)
    }

  override fun addChangeList(name: String, comment: String?): LocalChangeList = addChangeList(name, comment, null)

  override fun addChangeList(name: String, comment: String?, data: ChangeListData?): LocalChangeList {
    return runReadActionBlocking {
      synchronized(dataLock) {
        modifier.addChangeList(name, comment, data)
      }
    }
  }


  override fun removeChangeList(name: String) {
    runReadActionBlocking {
      synchronized(dataLock) {
        modifier.removeChangeList(name)
      }
    }
  }

  override fun removeChangeList(list: LocalChangeList) {
    removeChangeList(list.getName())
  }

  fun setDefaultChangeList(name: String, automatic: Boolean) {
    runReadActionBlocking {
      synchronized(dataLock) {
        modifier.setDefault(name, automatic)
      }
    }
  }

  override fun setDefaultChangeList(name: String) {
    setDefaultChangeList(name, false)
  }

  override fun setDefaultChangeList(list: LocalChangeList) {
    setDefaultChangeList(list, false)
  }

  override fun setDefaultChangeList(list: LocalChangeList, automatic: Boolean) {
    setDefaultChangeList(list.getName(), automatic)
  }

  override fun setReadOnly(name: String, value: Boolean): Boolean {
    return runReadActionBlocking {
      synchronized(dataLock) {
        modifier.setReadOnly(name, value)
      }
    }
  }

  override fun editName(fromName: String, toName: String): Boolean {
    return runReadActionBlocking {
      synchronized(dataLock) {
        modifier.editName(fromName, toName)
      }
    }
  }

  override fun editComment(name: String, newComment: String?): String? {
    return runReadActionBlocking {
      synchronized(dataLock) {
        modifier.editComment(name, newComment.orEmpty())
      }
    }
  }

  override fun editChangeListData(name: String, newData: ChangeListData?): Boolean {
    return runReadActionBlocking {
      synchronized(dataLock) {
        modifier.editData(name, newData)
      }
    }
  }

  override fun moveChangesTo(list: LocalChangeList, vararg changes: Change?) {
    moveChangesTo(list, changes.filterNotNull())
  }

  override fun moveChangesTo(list: LocalChangeList, changes: List<Change>) {
    runReadActionBlocking {
      synchronized(dataLock) {
        modifier.moveChangesTo(list.getName(), changes)
      }
    }
  }

  override fun getDefaultChangeList(): LocalChangeList =
    synchronized(dataLock) {
      worker.getDefaultList()
    }

  override fun getDefaultListName(): String =
    synchronized(dataLock) {
      worker.getDefaultList().getName()
    }

  fun notifyChangelistsChanged(path: FilePath, beforeChangeListsIds: List<String>, afterChangeListsIds: List<String>) {
    worker.notifyChangelistsChanged(path, beforeChangeListsIds, afterChangeListsIds)
  }

  /**
   * Notify that [VcsManagedFilesHolder] state was changed.
   */
  fun notifyUnchangedFileStatusChanged() {
    val isUnchangedUpdating = isInUpdate || isUnversionedInUpdateMode || isIgnoredInUpdateMode
    delayedNotificator.unchangedFileStatusChanged(!isUnchangedUpdating)
    delayedNotificator.changeListUpdateDone()
  }

  override fun getChangeListNameIfOnlyOne(changes: Array<Change>): String? =
    synchronized(dataLock) {
      return worker.getAffectedLists(changes.asList()).singleOrNull()?.name
    }

  override fun isInUpdate(): Boolean {
    return modifier.isInsideUpdate || showLocalChangesInvalidated
  }

  override fun getChange(file: VirtualFile): Change? {
    if (!file.isInLocalFileSystem) return null
    return getChange(VcsUtil.getFilePath(file))
  }

  override fun getAffectedLists(changes: Collection<Change>): List<LocalChangeList> {
    synchronized(dataLock) {
      return worker.getAffectedLists(changes)
    }
  }

  override fun getChangeLists(change: Change): List<LocalChangeList> = getAffectedLists(listOf(change))

  override fun getChangeLists(file: VirtualFile): List<LocalChangeList> {
    if (!file.isInLocalFileSystem) return listOf()
    synchronized(dataLock) {
      val change = worker.getChangeForPath(VcsUtil.getFilePath(file)) ?: return listOf()
      return getChangeLists(change)
    }
  }

  override fun getChangeList(change: Change): LocalChangeList? = getChangeLists(change).firstOrNull()

  override fun getChangeList(file: VirtualFile): LocalChangeList? = getChangeLists(file).firstOrNull()

  override fun getChange(file: FilePath?): Change? =
    synchronized(dataLock) {
      return worker.getChangeForPath(file)
    }

  override fun isUnversioned(file: VirtualFile): Boolean {
    if (!file.isInLocalFileSystem()) return false
    val vcsRoot = SlowOperations.knownIssue("IDEA-322445, EA-857508").use {
      ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(file)
    } ?: return false
    val filePath = VcsUtil.getFilePath(file)
    return runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.unversionedFileHolder.containsFile(filePath, vcsRoot)
      }
    }
  }

  override fun getStatus(path: FilePath): FileStatus = getStatus(path, path.getVirtualFile())

  override fun getStatus(file: VirtualFile): FileStatus {
    if (!file.isInLocalFileSystem) return FileStatus.NOT_CHANGED
    return getStatus(VcsUtil.getFilePath(file), file)
  }

  private fun getStatus(path: FilePath, file: VirtualFile?): FileStatus {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val vcsRoot = if (file != null) vcsManager.getVcsRootObjectFor(file) else vcsManager.getVcsRootObjectFor(path)
    if (vcsRoot == null) return FileStatus.NOT_CHANGED

    return runReadActionBlocking {
      synchronized(dataLock) {
        when {
          filesHolder.unversionedFileHolder.containsFile(path, vcsRoot) -> {
            FileStatus.UNKNOWN
          }
          filesHolder.resolvedMergeFilesHolder.containsFile(path, vcsRoot) -> {
            FileStatus.MERGE
          }
          file != null && filesHolder.modifiedWithoutEditingFileHolder.containsFile(file) -> {
            FileStatus.HIJACKED
          }
          filesHolder.ignoredFileHolder.containsFile(path, vcsRoot) -> {
            FileStatus.IGNORED
          }
          else -> {
            val status = worker.getStatus(path) ?: FileStatus.NOT_CHANGED
            if (file != null && FileStatus.NOT_CHANGED == status && filesHolder.switchedFileHolder.containsFile(file)) {
              FileStatus.SWITCHED
            }
            else {
              status
            }
          }
        }
      }
    }
  }

  override fun getChangesIn(dir: VirtualFile): Collection<Change> {
    if (!dir.isInLocalFileSystem) return emptySet()
    return getChangesIn(VcsUtil.getFilePath(dir))
  }

  override fun haveChangesUnder(vf: VirtualFile): ThreeState {
    if (!vf.isValid() || !vf.isDirectory()) return ThreeState.NO
    synchronized(dataLock) {
      return worker.haveChangesUnder(vf)
    }
  }

  override fun getChangesIn(dirPath: FilePath): Collection<Change> =
    allChanges.asSequence().filter { isChangeUnder(dirPath, it) }.toSet()

  override fun addUnversionedFiles(list: LocalChangeList?, files: List<VirtualFile>) {
    ScheduleForAdditionAction.Manager.addUnversionedFilesToVcs(project, list, files)
  }

  override fun addChangeListListener(listener: ChangeListListener, disposable: Disposable) {
    listeners.addListener(listener, disposable)
  }

  override fun addChangeListListener(listener: ChangeListListener) {
    listeners.addListener(listener)
  }

  override fun removeChangeListListener(listener: ChangeListListener) {
    listeners.removeListener(listener)
  }

  override fun commitChanges(changeList: LocalChangeList, changes: List<Change>) {
    doCommit(changeList, changes, false)
  }

  private fun doCommit(changeList: LocalChangeList, changes: List<Change>, synchronously: Boolean) {
    FileDocumentManager.getInstance().saveAllDocuments()

    val name = changeList.name
    val comment = changeList.comment

    val commitMessage = if (comment.isNullOrEmpty()) name else comment

    val commitState = ChangeListCommitState(changeList, changes, commitMessage)
    val committer = create(project, commitState, CommitContext(), name)

    committer.addResultHandler(ShowNotificationCommitResultHandler(committer))
    committer.runCommit(name, synchronously)
  }

  override fun loadState(element: Element) {
    val changeLists = ChangeListManagerSerialization.readExternal(element, project)

    synchronized(dataLock) {
      if (!initialUpdate) {
        LOG.warn("Local changes overwritten")
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
      }
      val areChangeListsEnabled = shouldEnableChangeLists()
      worker.setChangeListsEnabled(areChangeListsEnabled)
      if (areChangeListsEnabled) {
        worker.setChangeLists(changeLists)
      }
      else {
        disabledWorkerState = changeLists
      }
    }
    conflictTracker.loadState(element)
  }

  override fun getState(): Element {
    val element = Element("state")

    val areChangeListsEnabled: Boolean
    val changesToSave: List<LocalChangeList>?
    synchronized(dataLock) {
      areChangeListsEnabled = worker.areChangeListsEnabled()
      changesToSave = if (areChangeListsEnabled) worker.getChangeLists() else disabledWorkerState
    }
    ChangeListManagerSerialization.writeExternal(element, changesToSave, areChangeListsEnabled)
    conflictTracker.saveState(element)
    return element
  }

  override fun isIgnoredFile(file: VirtualFile): Boolean {
    if (!file.isInLocalFileSystem) return false
    return isIgnoredFile(VcsUtil.getFilePath(file))
  }

  override fun isIgnoredFile(file: FilePath): Boolean {
    val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootObjectFor(file) ?: return false
    return runReadActionBlocking {
      synchronized(dataLock) {
        filesHolder.ignoredFileHolder.containsFile(file, vcsRoot)
      }
    }
  }

  override fun getSwitchedBranch(file: VirtualFile): String? {
    if (!file.isInLocalFileSystem) return null
    return synchronized(dataLock) {
      ApplicationManagerEx.getApplicationEx().withLocksProhibited(DEADLOCK_ADVICE) {
        filesHolder.switchedFileHolder.getBranchForFile(file)
      }
    }
  }

  @RequiresEdt
  private fun updateChangeListAvailability() {
    if (project.isDisposed()) return

    val enabled = shouldEnableChangeLists()
    synchronized(dataLock) {
      if (enabled == worker.areChangeListsEnabled()) return
    }

    project.getMessageBus().syncPublisher(ChangeListAvailabilityListener.TOPIC).onBefore(!enabled)

    synchronized(dataLock) {
      assert(enabled != worker.areChangeListsEnabled())
      if (!enabled) {
        disabledWorkerState = worker.getChangeListsImpl()
      }

      // Schedule refresh to replace FakeRevisions with actual changes
      if (enabled) {
        VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
      }

      worker.setChangeListsEnabled(enabled)
      if (enabled) {
        disabledWorkerState?.let {
          worker.setChangeLists(it)
        }
      }
    }

    project.getMessageBus().syncPublisher(ChangeListAvailabilityListener.TOPIC).onAfter(enabled)
  }

  private fun shouldEnableChangeLists(): Boolean {
    val forceDisable = CommitModeManager.getInstance(project).getCurrentCommitMode().isLocalChangesTabHidden ||
                       Registry.`is`("vcs.disable.changelists", false)
    return !forceDisable
  }

  override fun areChangeListsEnabled(): Boolean {
    synchronized(dataLock) {
      return worker.areChangeListsEnabled()
    }
  }

  override fun getChangeListsNumber(): Int {
    synchronized(dataLock) {
      return worker.changeListsNumber
    }
  }

  // only a light attempt to show that some dirty scope request is asynchronously coming
  // for users to see changes are not valid
  // (commit -> asynch sync VFS -> asynch vcs dirty scope)
  fun showLocalChangesInvalidated() {
    showLocalChangesInvalidated = true
    stateProvider.setInUpdateMode(true)
  }

  private inner class DataHolder(
    val composite: FileHolderComposite,
    val changeListUpdater: ChangeListUpdater,
    private val wasEverythingDirty: Boolean,
  ) {
    val updatedWorker: ChangeListWorker
      get() = changeListUpdater.getUpdatedWorker()

    fun notifyStart() {
      if (wasEverythingDirty) {
        composite.cleanAll()
        changeListUpdater.notifyStartProcessingChanges(null)
      }
    }

    fun notifyStartProcessingChanges(scope: VcsModifiableDirtyScope) {
      if (!wasEverythingDirty) {
        composite.cleanUnderScope(scope)
        changeListUpdater.notifyStartProcessingChanges(scope)
      }

      composite.notifyVcsStarted(scope.getVcs())
    }

    fun notifyDoneProcessingChanges(scope: VcsDirtyScope) {
      if (!wasEverythingDirty) {
        changeListUpdater.notifyDoneProcessingChanges(delayedNotificator, scope)
      }
    }

    fun notifyEnd() {
      if (wasEverythingDirty) {
        changeListUpdater.notifyDoneProcessingChanges(delayedNotificator, null)
      }
    }

    fun finish() {
      changeListUpdater.finish()
    }
  }

  private class ChangesDeltaForwarder(
    private val project: Project,
    private val scheduler: ChangeListScheduler,
  ) : ChangeListDeltaListener {
    private val revisionsCache = RemoteRevisionsCache.getInstance(project)
    private val vcsManager = ProjectLevelVcsManager.getInstance(project)

    override fun modified(was: BaseRevision, become: BaseRevision) {
      doModify(was, become)
    }

    override fun added(baseRevision: BaseRevision) {
      doModify(baseRevision, baseRevision)
    }

    override fun removed(baseRevision: BaseRevision) {
      scheduler.submit(Runnable {
        val vcs = getVcs(baseRevision)
        if (vcs != null) {
          revisionsCache.changeRemoved(baseRevision.getPath(), vcs)
        }
        project.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirty(baseRevision.getPath())
      })
    }

    private fun doModify(was: BaseRevision, become: BaseRevision) {
      scheduler.submit(Runnable {
        val vcs = getVcs(was)
        if (vcs != null) {
          revisionsCache.changeUpdated(was.getPath(), vcs)
        }
        project.getMessageBus().syncPublisher(VcsAnnotationRefresher.LOCAL_CHANGES_CHANGED).dirty(become)
      })
    }

    private fun getVcs(baseRevision: BaseRevision): AbstractVcs? =
      baseRevision.getVcs() ?: vcsManager.getVcsFor(baseRevision.getFilePath())
  }

  internal class MyStartupActivity : VcsStartupActivity {
    override suspend fun execute(project: Project) {
      getInstanceImpl(project).startUpdater()
    }

    override val order: Int
      get() = VcsInitObject.CHANGE_LIST_MANAGER.order
  }

  companion object {
    @Topic.ProjectLevel
    val LISTS_LOADED_TOPIC: Topic<LocalChangeListsLoadedListener> =
      Topic(LocalChangeListsLoadedListener::class.java, Topic.BroadcastDirection.NONE)

    @JvmStatic
    fun getInstanceImpl(project: Project): ChangeListManagerImpl {
      return getInstance(project) as ChangeListManagerImpl
    }

    /**
     * Shows the proposal to delete one or more changelists that were default and became empty.
     *
     * @return true if the changelists have to be deleted, false if not.
     */
    private fun showRemoveEmptyChangeListsProposal(project: Project, config: VcsConfiguration, lists: Collection<ChangeList>): Boolean {
      if (lists.isEmpty()) return false

      val changeListName = if (lists.size == 1) {
        StringUtil.first(lists.first().name, 30, true)
      }
      else {
        lists.joinToString(separator = UIUtil.BR) { StringUtil.first(it.name, 30, true) }
      }
      val question = VcsBundle.message("changes.empty.changelists.no.longer.active", lists.size, changeListName)

      val option: VcsShowConfirmationOption = object : VcsShowConfirmationOption {
        override fun getValue(): VcsShowConfirmationOption.Value? {
          return config.REMOVE_EMPTY_INACTIVE_CHANGELISTS
        }

        override fun setValue(value: VcsShowConfirmationOption.Value?) {
          config.REMOVE_EMPTY_INACTIVE_CHANGELISTS = value
        }

        override fun isPersistent(): Boolean = true
      }

      return VcsConfirmationUtil.requestConfirmation(
        option = option,
        project = project,
        message = XmlStringUtil.wrapInHtml(question),
        title = VcsBundle.message("dialog.title.remove.empty.changelist"),
        icon = Messages.getQuestionIcon(),
        okActionName = VcsBundle.message("button.remove"),
        cancelActionName = CommonBundle.getCancelButtonText()
      )
    }

    @JvmStatic
    fun isUnder(change: Change, scope: VcsDirtyScope): Boolean {
      val before = change.getBeforeRevision()
      val after = change.getAfterRevision()
      return before != null && scope.belongsTo(before.getFile())
             || after != null && scope.belongsTo(after.getFile())
    }

    private fun isChangeUnder(parent: FilePath, change: Change): Boolean {
      val after = ChangesUtil.getAfterPath(change)
      val before = ChangesUtil.getBeforePath(change)
      return after != null && after.isUnder(parent, false) ||
             !Comparing.equal(before, after) && before != null && before.isUnder(parent, false)
    }
  }

  //region Test-Only
  @TestOnly
  fun commitChangesSynchronouslyWithResult(changeList: LocalChangeList, changes: List<Change>) {
    doCommit(changeList, changes, true)
  }

  @TestOnly
  fun waitUntilRefreshed() {
    LOG.debug("waitUntilRefreshed")
    assert(application.isUnitTestMode())
    project.service<VcsDirtyScopeVfsListener>().waitForAsyncTaskCompletion()
    updateRequestsQueue.waitUntilRefreshed()
    waitUpdateAlarm()
  }

  @TestOnly
  private fun waitUpdateAlarm() {
    assert(application.isUnitTestMode())
    val semaphore = Semaphore()
    semaphore.down()
    scheduler.submit(Runnable { semaphore.up() })
    if (application.isDispatchThread()) {
      while (!semaphore.waitFor(100)) {
        UIUtil.dispatchAllInvocationEvents()
      }
    }
    else {
      semaphore.waitFor()
    }
    LOG.debug("waitUpdateAlarm - finished")
  }

  @TestOnly
  fun stopEveryThingIfInTestMode() {
    assert(application.isUnitTestMode())
    scheduler.cancelAll()
  }

  @TestOnly
  fun waitEverythingDoneAndStopInTestMode() {
    assert(application.isUnitTestMode())
    scheduler.awaitAllAndStop()
    updateRequestsQueue.stop()
  }

  @TestOnly
  fun waitEverythingDoneInTestMode() {
    assert(application.isUnitTestMode())
    scheduler.awaitAll()
    LOG.debug("waitEverythingDoneInTestMode - finished")
  }

  @TestOnly
  fun forceStopInTestMode() {
    assert(application.isUnitTestMode())
    updateRequestsQueue.stop()
  }

  @TestOnly
  fun forceGoInTestMode() {
    assert(application.isUnitTestMode())
    updateRequestsQueue.forceGo()
  }

  @TestOnly
  fun ensureUpToDate() {
    assert(application.isUnitTestMode())
    waitUntilRefreshed()
  }
  //endregion

  // used in TeamCity
  @Suppress("OVERRIDE_DEPRECATION", "removal")
  override fun reopenFiles(paths: List<FilePath>) {
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project) as ReadonlyStatusHandlerImpl
    val savedOption = readonlyStatusHandler.state.SHOW_DIALOG
    readonlyStatusHandler.state.SHOW_DIALOG = false
    try {
      readonlyStatusHandler.ensureFilesWritable(paths.mapNotNull { it.virtualFile })
    }
    finally {
      readonlyStatusHandler.state.SHOW_DIALOG = savedOption
    }
  }
}

private fun <T, R> MutableCollection<T>.mapNotNullAndClear(mapper: (T) -> R?): List<R> {
  val result = mapNotNull(mapper)
  clear()
  return result
}
