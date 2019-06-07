// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangeListAdapter
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesViewI
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.MessageBusConnection
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.impl.VcsProjectLog.VCS_PROJECT_LOG_CHANGED
import com.jetbrains.changeReminder.plugin.UserSettings
import com.jetbrains.changeReminder.repository.FilesHistoryProvider

class PredictionService(val project: Project,
                        private val changeListManager: ChangeListManager,
                        private val changesViewManager: ChangesViewI) : Disposable {

  private data class PredictionRequirements(val dataManager: VcsLogData, val filesHistoryProvider: FilesHistoryProvider)

  private var predictionRequirements: PredictionRequirements? = null
  private lateinit var connection: MessageBusConnection
  private val userSettings = service<UserSettings>()
  private val LOCK = Object()

  private var _prediction: Collection<FilePath> = emptyList()

  // prediction contains only unmodified files
  val prediction: List<VirtualFile>
    get() = synchronized(LOCK) { _prediction.mapNotNull { if (changeListManager.getChange(it) == null) it.virtualFile else null } }

  val isReady: Boolean
    get() = synchronized(LOCK) { predictionRequirements?.dataManager?.dataPack?.isFull ?: false }

  private val taskController = object : PredictionController(project, "ChangeReminder Calculation", this, {
    synchronized(LOCK) {
      setPrediction(it)
    }
  }) {
    override fun inProgressChanged(value: Boolean) {
      changesViewManager.scheduleRefresh()
    }
  }

  val inProgress: Boolean
    get() = taskController.inProgress

  private val changeListsListener = object : ChangeListAdapter() {
    override fun changeListsChanged() {
      calculatePrediction()
    }
  }

  private val dataPackChangeListener = DataPackChangeListener {
    synchronized(LOCK) {
      predictionRequirements?.filesHistoryProvider?.clear()
      calculatePrediction()
    }
  }

  private val projectLogListener = object : VcsProjectLog.ProjectLogListener {
    override fun logCreated(manager: VcsLogManager) = synchronized(LOCK) {
      setDataManager(manager.dataManager)
    }

    override fun logDisposed(manager: VcsLogManager) = synchronized(LOCK) {
      removeDataManager()
    }
  }

  private val userSettingsListener = object : UserSettings.PluginStatusListener {
    override fun statusChanged(isEnabled: Boolean) {
      if (isEnabled) {
        startService()
      }
      else {
        shutdownService()
      }
    }
  }

  private val indexingFinishedListener = VcsLogIndex.IndexingFinishedListener { calculatePrediction() }

  init {
    if (userSettings.isPluginEnabled) {
      startService()
    }
    userSettings.addPluginStatusListener(userSettingsListener, this)
  }

  private fun setDataManager(dataManager: VcsLogData?) {
    dataManager ?: return
    dataManager.addDataPackChangeListener(dataPackChangeListener)
    dataManager.index.addListener(indexingFinishedListener)

    val filesHistoryProvider = dataManager.index.dataGetter?.let { FilesHistoryProvider(project, it) } ?: return
    predictionRequirements = PredictionRequirements(dataManager, filesHistoryProvider)
    calculatePrediction()
  }

  private fun removeDataManager() {
    setPrediction(emptyList())
    val (dataManager, filesHistoryProvider) = predictionRequirements ?: return
    predictionRequirements = null
    filesHistoryProvider.clear()

    dataManager.index.removeListener(indexingFinishedListener)
    dataManager.removeDataPackChangeListener(dataPackChangeListener)
  }

  private fun calculatePrediction() = synchronized(LOCK) {
    setPrediction(emptyList())
    val changes = changeListManager.defaultChangeList.changes
    if (changes.size > 25) return
    val (dataManager, filesHistoryProvider) = predictionRequirements ?: return
    if (dataManager.dataPack.isFull) {
      taskController.request(PredictionRequest(project, dataManager, filesHistoryProvider, changes))
    }
  }

  private fun startService() = synchronized(LOCK) {
    connection = project.messageBus.connect(this)
    connection.subscribe(VCS_PROJECT_LOG_CHANGED, projectLogListener)

    setDataManager(VcsProjectLog.getInstance(project).dataManager)

    changeListManager.addChangeListListener(changeListsListener)
  }

  private fun shutdownService() = synchronized(LOCK) {
    changeListManager.removeChangeListListener(changeListsListener)

    removeDataManager()

    connection.disconnect()
  }

  private fun setPrediction(newPrediction: Collection<FilePath>) {
    _prediction = newPrediction
    changesViewManager.scheduleRefresh()
  }

  override fun dispose() {
    if (userSettings.isPluginEnabled) {
      shutdownService()
    }
  }
}