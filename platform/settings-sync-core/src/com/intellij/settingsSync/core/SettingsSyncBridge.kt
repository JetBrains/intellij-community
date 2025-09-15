package com.intellij.settingsSync.core

import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.configurationStore.saveSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.progress.withProgressText
import com.intellij.settingsSync.core.SettingsSyncBridge.PushRequestMode.*
import com.intellij.settingsSync.core.communicator.RemoteCommunicatorHolder
import com.intellij.settingsSync.core.statistics.SettingsSyncEventsStatistics
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Handles events about settings change both from the current IDE, and from the server, merges the settings, logs them,
 * and provides the combined data to clients: both to the IDE and to the server.
 */
@ApiStatus.Internal
class SettingsSyncBridge(
  private val coroutineScope: CoroutineScope,
  private val appConfigPath: Path,
  private val settingsLog: SettingsLog,
  private val ideMediator: SettingsSyncIdeMediator,
  private val updateChecker: SettingsSyncUpdateChecker,
) {

  private val pendingExclusiveEvents = ContainerUtil.createConcurrentList<SyncSettingsEvent.ExclusiveEvent>()
  private val pendingEvents = ContainerUtil.createConcurrentList<SyncSettingsEvent.StandardEvent>()

  private val remoteCommunicator: SettingsSyncRemoteCommunicator
    get() = RemoteCommunicatorHolder.getRemoteCommunicator() ?: DummyCommunicator

  @Volatile
  private var queueJob: Job? = null

  @TestOnly
  internal val eventsProcessed = AtomicLong()

  // used in tests only
  private val eventProcessingFlag = AtomicBoolean()

  // used in tests only
  internal val queueSize: Int
    get() = pendingEvents.size + pendingExclusiveEvents.size + (if (eventProcessingFlag.get()) 1 else 0)

  val isInitialized
    get() = queueJob != null


  private val eventsMutex = Mutex()

  private val settingsChangeListener = object : SettingsSyncEventListener {
    override fun settingChanged(event: SyncSettingsEvent) {
      LOG.debug("Adding settings changed event $event to the queue")
      if (event is SyncSettingsEvent.ExclusiveEvent) { // such events will be processed separately from all others
        pendingExclusiveEvents.add(event)
        coroutineScope.launch {
          try {
            withTimeoutOrNull(60_000) {
              eventsMutex.withLock {
                LOG.debug("Lock obtained for exclusive event")
                processExclusiveEvent(event)
              }
            } ?: run {
              LOG.error("Could not acquire lock for exclusive event within 60 seconds", Throwable())
            }
          }
          catch (th: Throwable) {
            LOG.warn("An error occurred while obtaining lock for exclusive event", th)
          }
          finally {
            pendingExclusiveEvents.remove(event)
          }
        }
      }
      else if (event is SyncSettingsEvent.StandardEvent) {
        pendingEvents.addIfAbsent(event)
      }
    }
  }

  internal fun initialize(initMode: InitMode) {
    coroutineScope.launch {
      withProgressText(SettingsSyncBundle.message(initMode.messageKey)) {
        try {
          // We only due it on `PushToServer` because  with other init modes this method can be called too early in the IDE initialization process
          // and cause saving settings to fail — see fhttps://github.com/JetBrains/intellij-community/pull/2793#discussion_r1692737467 for context.
          if (initMode == InitMode.PushToServer) {
            // Flush settings explicitly – if this is not done before sending sync events, then remotely synced settings
            // might not contain the most up–to–date settings state (e.g. sync settings will be stale).
            saveIdeSettings()
          }
          settingsLog.initialize()

          // the queue is not activated initially => events will be collected but not processed until we perform all initialization tasks
          SettingsSyncEvents.getInstance().addListener(settingsChangeListener)
          ideMediator.activateStreamProvider()

          applyInitialChanges(initMode)

          startQueue()
        }
        catch (ex: Exception) {
          stopSyncingAndRollback(null, ex)
        }
      }
    }
  }

  private fun startQueue() {
    LOG.info("Starting settings sync queue")
    queueJob = coroutineScope.launch {
      while (true) {
        processPendingEvents()
        if (!SettingsSyncSettings.getInstance().syncEnabled && pendingEvents.isEmpty() && pendingExclusiveEvents.isEmpty()) {
          LOG.info("Sync disabled and no pending events. Stopping queue.")
          break
        }
        try {
          delay(1000)
        }
        catch (_: CancellationException) {
          queueJob = null
          LOG.info("queue processing was cancelled")
          break;
        }
      }
      LOG.info("Queue processing stopped")
    }
  }

  private suspend fun saveIdeSettings() {
    saveSettings(ApplicationManager.getApplication())
  }

  private suspend fun applyInitialChanges(initMode: InitMode) {
    val previousState = collectCurrentState()

    settingsLog.logExistingSettings()
    try {
      // We need to create this remote file before the first sync, otherwise the settings value (even if persisted) will be overwritten by
      // the sync-server-side value when `com.intellij.settingsSync.AbstractServerCommunicator#currentSnapshotFilePath` applies
      // the remote config received in the first sync.
      val fileExists = remoteCommunicator.isFileExists(CROSS_IDE_SYNC_MARKER_FILE)
      if (SettingsSyncLocalSettings.getInstance().isCrossIdeSyncEnabled) {
        if (!fileExists)
          remoteCommunicator.createFile(CROSS_IDE_SYNC_MARKER_FILE, "")
      }
      else {
        if (fileExists)
          remoteCommunicator.deleteFile(CROSS_IDE_SYNC_MARKER_FILE)
      }
      // we call updateOnSuccess, because the suspend methods below will be suspended on modality (if settings dialog is opened)
      // but we need to show the status in the configurable. By that time, we already know that communication was successful
      SettingsSyncStatusTracker.getInstance().updateOnSuccess()

      when (initMode) {
        is InitMode.TakeFromServer -> applySnapshotFromServer(initMode.cloudEvent)
        InitMode.PushToServer -> mergeAndPush(previousState.idePosition, previousState.cloudPosition, FORCE_PUSH)
        InitMode.JustInit -> mergeAndPush(previousState.idePosition, previousState.cloudPosition, PUSH_IF_NEEDED)
        is InitMode.MigrateFromOldStorage -> migrateFromOldStorage(initMode.migration)
      }
    }
    catch (e: Throwable) {
      if (initMode != InitMode.JustInit) {
        stopSyncingAndRollback(previousState, e)
      } else {
        LOG.warn("Cannot sync settings on initialization.", e)
        SettingsSyncStatusTracker.getInstance().updateOnError(e.localizedMessage ?: e.toString())
      }
    }
  }

  private suspend fun applySnapshotFromServer(cloudEvent: SyncSettingsEvent.CloudChange) {
    settingsLog.advanceMaster() // merge (preserve) 'ide' changes made by logging existing settings

    val masterPosition = settingsLog.forceWriteToMaster(cloudEvent.snapshot, "Remote changes to initialize settings by data from cloud")
    pushToIde(settingsLog.collectCurrentSnapshot(), masterPosition, cloudEvent.syncSettings)

    // normally we set cloud position only after successful push to cloud, but in this case we already take all settings from the cloud,
    // so no push is needed, and we know the cloud settings state.
    settingsLog.setCloudPosition(masterPosition)
    SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = cloudEvent.serverVersionId
  }

  private suspend fun migrateFromOldStorage(migration: SettingsSyncMigration) {
    TemplateSettings.getInstance() // Required for live templates to be migrated correctly, see IDEA-303831
    val migrationSnapshot = migration.getLocalDataIfAvailable(appConfigPath)
    if (migrationSnapshot != null) {
      settingsLog.applyIdeState(migrationSnapshot, "Migrate from old settings sync")
      LOG.info("Migration from old storage applied.")
      var masterPosition = settingsLog.advanceMaster() // merge (preserve) 'ide' changes made by logging existing settings & by migration

      when (val updateResult = remoteCommunicator.receiveUpdates()) {
        is UpdateResult.Success -> {
          LOG.info("There is a snapshot on the server => prefer server version over local migration data")
          val snapshot = updateResult.settingsSnapshot
          masterPosition = settingsLog.forceWriteToMaster(snapshot, "Remote changes to overwrite migration data by settings from cloud")
          settingsLog.setCloudPosition(masterPosition)

          SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = updateResult.serverVersionId
          SettingsSyncSettings.getInstance().syncEnabled = true
          pushToIde(settingsLog.collectCurrentSnapshot(), masterPosition, null)
        }
        is UpdateResult.FileDeletedFromServer -> {
          SettingsSyncSettings.getInstance().syncEnabled = false
          LOG.info("Snapshot on the server has been deleted => not enabling settings sync after migration")
        }
        is UpdateResult.Error -> {
          LOG.info("Error prevented checking server state: ${updateResult.message}")
          SettingsSyncSettings.getInstance().syncEnabled = false
          SettingsSyncStatusTracker.getInstance().updateOnError(updateResult.message)
        }
        UpdateResult.NoFileOnServer -> {
          LOG.info("No snapshot file on the server yet => pushing the migrated data to the cloud")
          forcePushToCloud(masterPosition)
          settingsLog.setCloudPosition(masterPosition)

          SettingsSyncSettings.getInstance().syncEnabled = true
          pushToIde(settingsLog.collectCurrentSnapshot(), masterPosition, null)
          migration.migrateCategoriesSyncStatus(appConfigPath, SettingsSyncSettings.getInstance())
          saveIdeSettings()
        }
      }
    }
    else {
      LOG.warn("Migration from old storage didn't happen, although it was identified as possible: no data to migrate")
      settingsLog.advanceMaster() // merge (preserve) 'ide' changes made by logging existing settings
    }
  }

  private fun forcePushToCloud(masterPosition: SettingsLog.Position) {
    pushAndHandleResult(true, masterPosition, onRejectedPush = {
      LOG.error("Reject shouldn't happen when force push is used")
      SettingsSyncStatusTracker.getInstance().updateOnError(SettingsSyncBundle.message("notification.title.push.error"))
    })
  }

  internal sealed class InitMode(val messageKey: String) {
    object JustInit
      : InitMode("enable.sync.check.server.data.progress")

    class TakeFromServer(val cloudEvent: SyncSettingsEvent.CloudChange)
      : InitMode("enable.sync.get.from.server.progress")

    class MigrateFromOldStorage(val migration: SettingsSyncMigration)
      : InitMode("enable.sync.check.server.data.progress")

    object PushToServer
      : InitMode("enable.sync.push.to.server.progress")
  }

  private suspend fun processExclusiveEvent(event: SyncSettingsEvent.ExclusiveEvent) {
    when (event) {
      is SyncSettingsEvent.CrossIdeSyncStateChanged -> {
        LOG.info("Cross-ide sync state changed to: " + event.isCrossIdeSyncEnabled)
        if (event.isCrossIdeSyncEnabled) {
          remoteCommunicator.createFile(CROSS_IDE_SYNC_MARKER_FILE, "")
        }
        else {
          remoteCommunicator.deleteFile(CROSS_IDE_SYNC_MARKER_FILE)
        }
        forcePushToCloud(settingsLog.getMasterPosition())
      }
      is SyncSettingsEvent.SyncRequest -> {
        checkServer()
      }
      is SyncSettingsEvent.RestoreSettingsSnapshot -> {
        val previousState = collectCurrentState()
        settingsLog.restoreStateAt(event.hash)
        pushToIde(settingsLog.collectCurrentSnapshot(), settingsLog.getIdePosition(), null)
        mergeAndPush(previousState.idePosition, previousState.cloudPosition, MUST_PUSH)
        event.onComplete.run()
      }
    }
  }

  private suspend fun processPendingEvents(force: Boolean = false) {
    if (pendingEvents.isEmpty()) {
      LOG.debug("Pending events is empty")
      return
    }
    if (force) {
      withTimeoutOrNull(60_000) {
        eventsMutex.withLock {
          processPendingEventsUnderLock()
        }
      } ?: run {
        LOG.error("Could not acquire lock for exclusive event within 60 seconds", Throwable())
      }
    } else {
      if (eventsMutex.tryLock()) {
        processPendingEventsUnderLock()
        eventsMutex.unlock()
      } else {
        LOG.debug("Events are being processed by another coroutine, will retry later")
      }
    }
  }

  private suspend fun processPendingEventsUnderLock() {
    try {
      while (pendingEvents.isNotEmpty()) {
        var pushRequestMode: PushRequestMode = PUSH_IF_NEEDED
        var mergeAndPushAfterProcessingEvents = true
        val previousState = collectCurrentState()
        try {
          val event = pendingEvents.removeAt(0)
          eventProcessingFlag.set(true)
          LOG.info("Processing event $event")
          when (event) {
            is SyncSettingsEvent.IdeChange -> {
              settingsLog.applyIdeState(event.snapshot, "Local changes made in the IDE")
            }
            is SyncSettingsEvent.CloudChange -> {
              settingsLog.applyCloudState(event.snapshot, "Remote changes")
              SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = event.serverVersionId
            }
            is SyncSettingsEvent.LogCurrentSettings -> {
              settingsLog.logExistingSettings()
            }
            is SyncSettingsEvent.MustPushRequest -> {
              pushRequestMode = MUST_PUSH
            }
            is SyncSettingsEvent.DeleteServerData -> {
              mergeAndPushAfterProcessingEvents = false
              stopSyncingAndRollback(previousState)
              deleteServerData(event.afterDeleting)
            }
            SyncSettingsEvent.DeletedOnCloud -> {
              mergeAndPushAfterProcessingEvents = false
              stopSyncingAndRollback(previousState)
            }
          }

          if (mergeAndPushAfterProcessingEvents) {
            mergeAndPush(previousState.idePosition, previousState.cloudPosition, pushRequestMode)
          }
          eventsProcessed.incrementAndGet()
        }
        catch (exception: Throwable) {
          stopSyncingAndRollback(previousState, exception)
        }
        finally {
          eventProcessingFlag.set(false)
        }
      }
    }
    catch (th: Throwable) {
      LOG.error("Error occurred while processing pending events", th)
    }
  }

  private fun deleteServerData(afterDeleting: (DeleteServerDataResult) -> Unit) {
    val deletionSnapshot = SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo(), isDeleted = true),
                                            emptySet(), null, emptyMap(), emptySet())
    val pushResult = pushToCloud(deletionSnapshot, force = true)
    LOG.info("Deleting server data. Result: $pushResult")
    when (pushResult) {
      is SettingsSyncPushResult.Success -> {
        afterDeleting(DeleteServerDataResult.Success)
      }
      is SettingsSyncPushResult.Error -> {
        afterDeleting(DeleteServerDataResult.Error(pushResult.message))
      }
      SettingsSyncPushResult.Rejected -> {
        afterDeleting(DeleteServerDataResult.Error("Deletion rejected by server"))
      }
    }
  }

  private fun checkServer() {
    when (val result = remoteCommunicator.checkServerState()) {
      is ServerState.UpdateNeeded -> {
        LOG.info("Updating from server")
        updateChecker.scheduleUpdateFromServer()
        // the push will happen automatically after updating and merging (if there is anything to merge)
      }
      ServerState.FileNotExists -> {
        LOG.info("No file on server, will push local settings")
        SettingsSyncEvents.getInstance().fireSettingsChanged(SyncSettingsEvent.MustPushRequest)
      }
      ServerState.UpToDate -> {
        LOG.debug("Updating settings is not needed")
        // Clear the error state, if any
        SettingsSyncStatusTracker.getInstance().updateOnSuccess()
      }
      is ServerState.Error -> {
        // The error is already logged in `SettingsSyncRemoteCommunicator.checkServerState`, but we need to set
        // an error state for the UI to display failed sync information in the top-right corner settings menu
        SettingsSyncStatusTracker.getInstance().updateOnError(
          SettingsSyncBundle.message("notification.title.push.error") + ": " + result.message)
      }
    }
  }

  private class CurrentState(
    val masterPosition: SettingsLog.Position,
    val idePosition: SettingsLog.Position,
    val cloudPosition: SettingsLog.Position,
    val knownServerId: String?,
  ) {
    override fun toString(): String {
      return "CurrentState(masterPosition=$masterPosition, idePosition=$idePosition, cloudPosition=$cloudPosition, knownServerId=$knownServerId)"
    }
  }

  private fun collectCurrentState(): CurrentState = CurrentState(settingsLog.getMasterPosition(),
                                                                 settingsLog.getIdePosition(),
                                                                 settingsLog.getCloudPosition(),
                                                                 SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId)

  private fun stopSyncingAndRollback(previousState: CurrentState?, exception: Throwable? = null) {
    if (exception != null) {
      LOG.error("Couldn't apply settings. Settings sync will be disabled.", exception)
      SettingsSyncEventsStatistics.DISABLED_AUTOMATICALLY.log(SettingsSyncEventsStatistics.AutomaticDisableReason.EXCEPTION)
    }
    else {
      LOG.info("Settings Sync is switched off. Rolling back.")
    }
    SettingsSyncSettings.getInstance().syncEnabled = false
    if (exception != null) {
      SettingsSyncStatusTracker.getInstance().updateOnError(exception.localizedMessage ?: exception.toString())
    }

    ideMediator.removeStreamProvider()
    SettingsSyncEvents.getInstance().removeListener(settingsChangeListener)
    pendingEvents.clear()
    if (previousState != null) {
      rollback(previousState)
    }

    // for tests it is important to have it the last statement, otherwise waitForAllExecuted can finish before rollback
    queueJob?.cancel()
  }

  private fun rollback(previousState: CurrentState) {
    try {
      LOG.warn("Rolling back to previous state: $previousState")
      SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = previousState.knownServerId
      settingsLog.setIdePosition(previousState.idePosition)
      settingsLog.setCloudPosition(previousState.cloudPosition)
      settingsLog.setMasterPosition(previousState.masterPosition)
      // we don't need to roll back the state of the IDE here, because it is the latest stage of mergeAndPush which can fail
      // (pushing can fail also, but it is a normal failure which doesn't need to roll everything back and turn the sync off
    }
    catch (e: Throwable) {
      LOG.error("Couldn't rollback to the previous successful state", e)
    }
  }

  private suspend fun mergeAndPush(
    previousIdePosition: SettingsLog.Position,
    previousCloudPosition: SettingsLog.Position,
    pushRequestMode: PushRequestMode,
  ) {
    val newIdePosition = settingsLog.getIdePosition()
    val newCloudPosition = settingsLog.getCloudPosition()
    val masterPosition: SettingsLog.Position
    if (newIdePosition != previousIdePosition || newCloudPosition != previousCloudPosition) {
      // move master to the actual position. It can be a fast-forward to either ide, or cloud changes, or it can be a merge
      masterPosition = settingsLog.advanceMaster()
    }
    else {
      // there were only fake events without actual changes to the repository => master doesn't need to be changed either
      masterPosition = settingsLog.getMasterPosition()
    }

    if (newIdePosition != masterPosition) { // master has advanced further that ide => the ide needs to be updated
      pushToIde(settingsLog.collectCurrentSnapshot(), masterPosition, null)
    }

    if (newCloudPosition != masterPosition || pushRequestMode == MUST_PUSH || pushRequestMode == FORCE_PUSH) {
      pushAndHandleResult(pushRequestMode == FORCE_PUSH, masterPosition, onRejectedPush = {
        // todo add protection against potential infinite reject-update-reject cycle
        //  (it would indicate some problem, but still shouldn't cycle forever)

        // In the case of reject we'll just "wait" for the next update event:
        // it will be processed in the next session anyway
        if (pendingEvents.none { it is SyncSettingsEvent.CloudChange }) {
          // not to wait for too long, schedule an update right away unless it has already been scheduled
          updateChecker.scheduleUpdateFromServer()
        }
      })
    }
    else {
      LOG.debug("Nothing to push")
    }
  }

  private fun pushAndHandleResult(force: Boolean, positionToSetCloudBranch: SettingsLog.Position, onRejectedPush: () -> Unit) {
    val pushResult: SettingsSyncPushResult = pushToCloud(settingsLog.collectCurrentSnapshot(), force)
    LOG.info("Result of pushing settings to the cloud: $pushResult")
    when (pushResult) {
      is SettingsSyncPushResult.Success -> {
        settingsLog.setCloudPosition(positionToSetCloudBranch)
        SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId = pushResult.serverVersionId
        SettingsSyncStatusTracker.getInstance().updateOnSuccess()
      }
      is SettingsSyncPushResult.Error -> {
        SettingsSyncStatusTracker.getInstance().updateOnError(
          SettingsSyncBundle.message("notification.title.push.error") + ": " + pushResult.message)
      }
      SettingsSyncPushResult.Rejected -> {
        onRejectedPush()
      }
    }
  }

  private enum class PushRequestMode {
    PUSH_IF_NEEDED,
    MUST_PUSH,
    FORCE_PUSH
  }

  private fun pushToCloud(settingsSnapshot: SettingsSnapshot, force: Boolean): SettingsSyncPushResult {
    val versionId = SettingsSyncLocalSettings.getInstance().knownAndAppliedServerId
    if (force) {
      return remoteCommunicator.push(settingsSnapshot, force = true, versionId)
    }
    else {
      when (remoteCommunicator.checkServerState()) {
        is ServerState.UpdateNeeded -> {
          return SettingsSyncPushResult.Rejected
        }
        is ServerState.FileNotExists -> {
          return remoteCommunicator.push(settingsSnapshot, force = true, versionId)
        }
        else -> {
          return remoteCommunicator.push(settingsSnapshot, force = false, versionId)
        }
      }
    }
  }

  private suspend fun pushToIde(settingsSnapshot: SettingsSnapshot, targetPosition: SettingsLog.Position, syncSettings: SettingsSyncState?) {
    ideMediator.applyToIde(settingsSnapshot, syncSettings)
    settingsLog.setIdePosition(targetPosition)
    LOG.info("Applied settings to the IDE.")
  }

  @TestOnly
  suspend fun waitForAllExecuted() {
    processPendingEvents(force = true)
    val startTime = System.currentTimeMillis()
    while (System.currentTimeMillis() - startTime < 10000 && queueSize > 0) {
      delay(10)
    }
    if (queueSize > 0) {
      LOG.warn("Queue size > 0 !!!!!!")
    }
  }

  @TestOnly
  internal fun stop() {
    stopSyncingAndRollback(null, null)
  }

  private object DummyCommunicator : SettingsSyncRemoteCommunicator {
    override val userId: String
      get() = ""

    override fun setTemporary(isTemporary: Boolean) {
      // do nothing
    }

    override fun checkServerState(): ServerState {
      val errorMsg = "Cannot check server state - no communicator provided"
      LOG.info(errorMsg)
      return ServerState.Error(errorMsg)
    }

    override fun receiveUpdates(): UpdateResult {
      val errorMsg = "Cannot received updates - no communicator provided"
      LOG.info(errorMsg)
      return UpdateResult.Error(errorMsg)
    }

    override fun push(snapshot: SettingsSnapshot, force: Boolean, expectedServerVersionId: String?): SettingsSyncPushResult {
      val errorMsg = "Cannot push - no communicator provided"
      LOG.info(errorMsg)
      return SettingsSyncPushResult.Error(errorMsg)
    }

    override fun createFile(filePath: String, content: String) {
      LOG.info("Cannot create file '$filePath' - no communicator provided")
    }

    override fun deleteFile(filePath: String) {
      LOG.info("Cannot delete file '$filePath' - no communicator provided")
    }

    override fun isFileExists(filePath: String): Boolean {
      LOG.info("Cannot check if file '$filePath' exists - no communicator provided")
      return false;
    }
  }


  companion object {
    private val LOG = logger<SettingsSyncBridge>()
  }
}