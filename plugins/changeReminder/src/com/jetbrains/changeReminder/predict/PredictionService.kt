// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.changeReminder.predict

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing.haveEqualElements
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.changeReminder.getGitRootFiles
import com.jetbrains.changeReminder.plugin.UserSettings
import com.jetbrains.changeReminder.repository.FilesHistoryProvider
import com.jetbrains.changeReminder.stats.ChangeReminderChangeListChangedEvent
import com.jetbrains.changeReminder.stats.ChangeReminderNodeExpandedEvent
import git4idea.history.GitHistoryTraverser
import git4idea.history.GitHistoryTraverserListener
import git4idea.history.getTraverser
import git4idea.history.subscribeForGitHistoryTraverserCreation
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener

@Service(Service.Level.PROJECT)
internal class PredictionService(val project: Project) : Disposable {
  private var isDisposed = false

  private data class PredictionRequirements(val filesHistoryProvider: FilesHistoryProvider)

  private var predictionRequirements: PredictionRequirements? = null
  private lateinit var serviceDisposable: Disposable
  private val userSettings = service<UserSettings>()
  private val LOCK = Object()

  private var predictionRequestDisposable = Disposer.newDisposable()

  private var predictionData: PredictionData = PredictionData.EmptyPrediction(PredictionData.EmptyPredictionReason.SERVICE_INIT)

  val predictionDataToDisplay
    get() = predictionData

  val isReadyToDisplay: Boolean
    get() = predictionData is PredictionData.Prediction

  private val taskController = object : PredictionController(project, "ChangeReminder Calculation", this, {
    synchronized(LOCK) {
      setPrediction(it)
    }
  }) {
    override fun inProgressChanged(value: Boolean) {
      refreshChangesBrowser()
    }
  }

  val inProgress: Boolean
    get() = taskController.inProgress

  private val gitHistoryTraverserListener = object : GitHistoryTraverserListener {
    override fun traverserCreated(newTraverser: GitHistoryTraverser) = synchronized(LOCK) {
      updateTraverser(newTraverser)
      Disposer.register(newTraverser, Disposable { onTraverserDisposed() })
    }

    override fun graphUpdated() = synchronized(LOCK) {
      predictionRequirements?.filesHistoryProvider?.clear()
      setEmptyPrediction(PredictionData.EmptyPredictionReason.GRAPH_CHANGED)
      calculatePrediction()
    }
  }

  private val changeListsListener = object : ChangeListAdapter() {
    private var lastChanges: Collection<Change> = listOf()
    override fun changeListsChanged() {
      val changes = ChangeListManager.getInstance(project).defaultChangeList.changes
      if (!haveEqualElements(changes, lastChanges)) {
        if (changes.size <= Registry.intValue("vcs.changeReminder.changes.limit")) {
          val prevFiles = lastChanges.map { ChangesUtil.getFilePath(it) }
          val curFiles = changes.map { ChangesUtil.getFilePath(it) }
          ChangeReminderChangeListChangedEvent(prevFiles, predictionData, curFiles).logEvent(project)
        }
        calculatePrediction()
        lastChanges = changes
      }
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

  private val nodeExpandedListener = object : TreeExpansionListener {
    private var view: ChangesListView? = null
    override fun treeExpanded(event: TreeExpansionEvent?) {
      if (event == null) {
        return
      }
      val predictionData = TreeUtil.findObjectInPath(event.path, PredictionData.Prediction::class.java) ?: return
      ChangeReminderNodeExpandedEvent(predictionData).logEvent(project)
    }

    override fun treeCollapsed(event: TreeExpansionEvent?) {}

    fun tryToSubscribe() {
      if (view != null) {
        return
      }

      val changeListViewPanel =
        ChangesViewContentManager.getInstance(project).getActiveComponent(ChangesViewManager.ChangesViewToolWindowPanel::class.java)
        ?: return
      view = UIUtil.findComponentOfType(changeListViewPanel, ChangesListView::class.java)?.also {
        it.addTreeExpansionListener(this)
      }
    }

    fun unsubscribe() {
      view?.removeTreeExpansionListener(this)
    }
  }

  init {
    if (userSettings.isPluginEnabled) {
      startService()
    }
    userSettings.addPluginStatusListener(userSettingsListener, this)
  }

  private fun updateTraverser(traverser: GitHistoryTraverser) {
    if (predictionRequirements?.filesHistoryProvider?.traverser == traverser) {
      return
    }

    predictionRequirements = PredictionRequirements(FilesHistoryProvider(traverser))
    calculatePrediction()
  }

  private fun onTraverserDisposed() {
    setEmptyPrediction(PredictionData.EmptyPredictionReason.TRAVERSER_INVALID)
    val (filesHistoryProvider) = predictionRequirements ?: return
    predictionRequirements = null
    filesHistoryProvider.clear()
  }

  private fun calculatePrediction() = synchronized(LOCK) {
    nodeExpandedListener.tryToSubscribe()

    if (!Disposer.isDisposed(predictionRequestDisposable)) {
      Disposer.dispose(predictionRequestDisposable)
    }
    predictionRequestDisposable = Disposer.newDisposable()

    val changes = ChangeListManager.getInstance(project).defaultChangeList.changes
    if (changes.size > Registry.intValue("vcs.changeReminder.changes.limit")) {
      setEmptyPrediction(PredictionData.EmptyPredictionReason.TOO_MANY_FILES)
      return
    }
    val (filesHistoryProvider) = predictionRequirements ?: let {
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
    val rootFiles = getGitRootFiles(project, changeListFiles)
    val roots = rootFiles.keys
    filesHistoryProvider.traverser.addIndexingListener(roots, predictionRequestDisposable) { indexedRoots ->
      taskController.request(
        PredictionRequest(filesHistoryProvider, indexedRoots.map { it to rootFiles.getValue(it.root) }.toMap())
      )
    }
  }

  private fun startService() = synchronized(LOCK) {
    serviceDisposable = Disposer.newDisposable()
    val connection = project.messageBus.connect(serviceDisposable)
    connection.subscribe(ChangeListListener.TOPIC, changeListsListener)

    subscribeForGitHistoryTraverserCreation(project, gitHistoryTraverserListener, serviceDisposable)
    getTraverser(project)?.let {
      updateTraverser(it)
    }
  }

  private fun shutdownService() = synchronized(LOCK) {
    Disposer.dispose(predictionRequestDisposable)

    nodeExpandedListener.unsubscribe()

    onTraverserDisposed()

    Disposer.dispose(serviceDisposable)
  }

  private fun setEmptyPrediction(reason: PredictionData.EmptyPredictionReason) {
    setPrediction(PredictionData.EmptyPrediction(reason))
  }

  private fun setPrediction(newPrediction: PredictionData) {
    predictionData = newPrediction
    refreshChangesBrowser()
  }

  override fun dispose() {
    isDisposed = true
    if (userSettings.isPluginEnabled) {
      shutdownService()
    }
  }

  private fun refreshChangesBrowser() {
    if (!isDisposed) {
      project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
    }
  }
}