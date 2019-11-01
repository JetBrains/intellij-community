// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing.haveEqualElements
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.jetbrains.changeReminder.plugin.UserSettings
import com.jetbrains.changeReminder.repository.FilesHistoryProvider

class PredictionService(val project: Project) : Disposable {
  private val changesViewManager = ChangesViewManager.getInstance(project)
  private val changeListManager = ChangeListManager.getInstance(project)

  private data class PredictionRequirements(val dataManager: VcsLogData, val filesHistoryProvider: FilesHistoryProvider)

  private var predictionRequirements: PredictionRequirements? = null
  private lateinit var projectLogListenerDisposable: Disposable
  private val userSettings = service<UserSettings>()
  private val LOCK = Object()

  private var predictionData: PredictionData = PredictionData.EmptyPrediction(PredictionData.EmptyPredictionReason.SERVICE_INIT)

  val predictionToDisplay: Collection<VirtualFile>
    get() = predictionData.predictionToDisplay

  val isReadyToDisplay: Boolean
    get() = predictionData is PredictionData.Prediction

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
    private var lastChanges: Collection<Change> = listOf()
    override fun changeListsChanged() {
      val changes = changeListManager.defaultChangeList.changes
      if (!haveEqualElements(changes, lastChanges)) {
        calculatePrediction()
        lastChanges = changes
      }
    }
  }

  private val dataPackChangeListener = DataPackChangeListener {
    synchronized(LOCK) {
      predictionRequirements?.filesHistoryProvider?.clear()
      setEmptyPrediction(PredictionData.EmptyPredictionReason.DATA_PACK_CHANGED)
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
    if (predictionRequirements?.dataManager == dataManager) return

    dataManager.addDataPackChangeListener(dataPackChangeListener)
    dataManager.index.addListener(indexingFinishedListener)

    val filesHistoryProvider = dataManager.index.dataGetter?.let { FilesHistoryProvider(project, dataManager, it) } ?: return
    predictionRequirements = PredictionRequirements(dataManager, filesHistoryProvider)
    calculatePrediction()
  }

  private fun removeDataManager() {
    setEmptyPrediction(PredictionData.EmptyPredictionReason.DATA_MANAGER_REMOVED)
    val (dataManager, filesHistoryProvider) = predictionRequirements ?: return
    predictionRequirements = null
    filesHistoryProvider.clear()

    dataManager.index.removeListener(indexingFinishedListener)
    dataManager.removeDataPackChangeListener(dataPackChangeListener)
  }

  private fun calculatePrediction() = synchronized(LOCK) {
    val changes = changeListManager.defaultChangeList.changes
    if (changes.size > Registry.intValue("vcs.changeReminder.changes.limit")) {
      setEmptyPrediction(PredictionData.EmptyPredictionReason.TOO_MANY_FILES)
      return
    }
    val (dataManager, filesHistoryProvider) = predictionRequirements ?: let {
      setEmptyPrediction(PredictionData.EmptyPredictionReason.REQUIREMENTS_NOT_MET)
      return
    }
    val changeListFiles = changes.map { ChangesUtil.getFilePath(it) }
    val currentPredictionData = predictionData
    if (currentPredictionData is PredictionData.Prediction &&
        haveEqualElements(currentPredictionData.requestedFiles, changeListFiles)) {
      setPrediction(currentPredictionData)
      return
    }
    if (dataManager.dataPack.isFull) {
      taskController.request(PredictionRequest(project, dataManager, filesHistoryProvider, changeListFiles))
    }
    else {
      setEmptyPrediction(PredictionData.EmptyPredictionReason.DATA_PACK_IS_NOT_FULL)
    }
  }

  private fun startService() = synchronized(LOCK) {
    projectLogListenerDisposable = Disposer.newDisposable()
    project.messageBus.connect(projectLogListenerDisposable).subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, projectLogListener)

    setDataManager(VcsProjectLog.getInstance(project).dataManager)

    changeListManager.addChangeListListener(changeListsListener)
  }

  private fun shutdownService() = synchronized(LOCK) {
    changeListManager.removeChangeListListener(changeListsListener)

    removeDataManager()

    Disposer.dispose(projectLogListenerDisposable)
  }

  private fun setEmptyPrediction(reason: PredictionData.EmptyPredictionReason) {
    setPrediction(PredictionData.EmptyPrediction(reason))
  }

  private fun setPrediction(newPrediction: PredictionData) {
    predictionData = newPrediction
    changesViewManager.scheduleRefresh()
  }

  override fun dispose() {
    if (userSettings.isPluginEnabled) {
      shutdownService()
    }
  }
}