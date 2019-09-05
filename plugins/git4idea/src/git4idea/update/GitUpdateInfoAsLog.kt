// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ContentUtilEx
import com.intellij.util.text.DateFormatUtil
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogFilterCollection.*
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.util.containsAll
import com.intellij.vcs.log.visible.VcsLogFiltererImpl
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.VisiblePackChangeListener
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.GitRevisionNumber
import git4idea.history.GitHistoryUtils
import git4idea.merge.MergeChangeCollector
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import org.jetbrains.annotations.CalledInBackground
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.ArrayList

private val LOG = logger<GitUpdateInfoAsLog>()

class GitUpdateInfoAsLog(private val project: Project,
                         private val ranges: Map<GitRepository, HashRange>) {

  private val projectLog: VcsProjectLog = VcsProjectLog.getInstance(project)

  private var notificationShown: Boolean = false // accessed only from EDT

  class NotificationData(val updatedFilesCount: Int,
                         val receivedCommitsCount: Int,
                         val filteredCommitsCount: Int?,
                         val viewCommitAction: Runnable)

  private class CommitsAndFiles(val updatedFilesCount: Int, val receivedCommitsCount: Int)

  @CalledInBackground
  fun calculateDataAndCreateLogTab(): NotificationData? {
    val commitsAndFiles = calculateDataFromGit()

    if (commitsAndFiles == null) {
      return null
    }

    val logManager = VcsLogContentUtil.getOrCreateLog(project)
    if (!isPathFilterSet()) {
      // if no path filters is set, we don't need the log to show the notification
      // => schedule the log tab and return the data
      val (logUi, factory) = createLogTabInEdtAndWait(logManager)
      return NotificationData(commitsAndFiles.updatedFilesCount, commitsAndFiles.receivedCommitsCount, null,
                              getViewCommitsAction(logUi, factory))
    }
    else {
      return waitForLogRefreshAndCalculate(logManager, commitsAndFiles)
    }
  }

  private fun isPathFilterSet(): Boolean {
    return project.service<GitUpdateProjectInfoLogProperties>().getFilterValues(STRUCTURE_FILTER.name) != null
  }

  @CalledInBackground
  private fun waitForLogRefreshAndCalculate(logManager: VcsLogManager, commitsAndFiles: CommitsAndFiles): NotificationData {
    val dataSupplier = CompletableFuture<NotificationData>()

    val listener = object : DataPackChangeListener {
      override fun onDataPackChange(dataPack: DataPack) {
        createLogTabAndCalculateIfRangesAreReachable(dataPack, logManager, commitsAndFiles, dataSupplier, this)
      }
    }

    projectLog.dataManager?.addDataPackChangeListener(listener)

    ApplicationManager.getApplication().invokeLater {
      // the log may be refreshed before we subscribe to the listener
      createLogTabAndCalculateIfRangesAreReachable(logManager.dataManager.dataPack, logManager, commitsAndFiles, dataSupplier, listener)
    }

    BackgroundTaskUtil.awaitWithCheckCanceled(dataSupplier)
    return dataSupplier.get()
  }

  @CalledInAwt
  private fun createLogTabAndCalculateIfRangesAreReachable(dataPack: DataPack,
                                                           logManager: VcsLogManager,
                                                           commitsAndFiles: CommitsAndFiles,
                                                           dataSupplier: CompletableFuture<NotificationData>,
                                                           listener: DataPackChangeListener) {
    if (!notificationShown && areRangesInDataPack(projectLog, dataPack)) {
      notificationShown = true
      projectLog.dataManager?.removeDataPackChangeListener(listener)
      createLogTab(logManager) { logUi: VcsLogUiImpl, factory: MyLogUiFactory ->
        MyVisiblePackChangeListener(logUi, factory, commitsAndFiles, dataSupplier)
      }
    }
  }

  private fun areRangesInDataPack(log: VcsProjectLog, dataPack: DataPack): Boolean {
    return dataPack.containsAll(ranges.asIterable().map { CommitId(it.value.end, it.key.root) }, log.dataManager!!.storage)
  }

  private fun calculateDataFromGit(): CommitsAndFiles? {
    val updatedCommitsCount = calcUpdatedCommitsCount()
    if (updatedCommitsCount == 0) {
      return null
    }
    val updatedFilesCount = calcUpdatedFilesCount()
    return CommitsAndFiles(updatedFilesCount, updatedCommitsCount)
  }

  private fun createLogTabInEdtAndWait(logManager: VcsLogManager): Pair<VcsLogUiImpl, MyLogUiFactory> {
    val logUi = Ref.create<Pair<VcsLogUiImpl, MyLogUiFactory>>()
    ApplicationManager.getApplication().invokeAndWait {
      logUi.set(createLogTab(logManager, null))
    }
    return logUi.get()
  }

  private fun getViewCommitsAction(logUi: VcsLogUiImpl, factory: MyLogUiFactory): Runnable {
    return Runnable {
      projectLog.logManager?.let {
        val found = VcsLogContentUtil.selectLogUi(project, logUi)
        if (!found) {
          createLogUiAndTab(it, factory, select = true)
        }
      } ?: VcsLogContentUtil.showLogIsNotAvailableMessage(project)
    }
  }

  private fun createLogTab(logManager: VcsLogManager,
                           listenerGetter: ((VcsLogUiImpl, MyLogUiFactory) -> VisiblePackChangeListener)?):
    Pair<VcsLogUiImpl, MyLogUiFactory> {
    val rangeFilter = VcsLogFilterObject.fromRange(ranges.values.map {
      VcsLogRangeFilter.RefRange(it.start.asString(), it.end.asString())
    })
    val logUiFactory = MyLogUiFactory(logManager, rangeFilter, listenerGetter)
    val logUi = createLogUiAndTab(logManager, logUiFactory, select = false)
    return Pair(logUi, logUiFactory)
  }

  private fun createLogUiAndTab(logManager: VcsLogManager, logUiFactory: MyLogUiFactory, select: Boolean): VcsLogUiImpl {
    val logUi = logManager.createLogUi(logUiFactory, true)
    val panel = VcsLogPanel(logManager, logUi)
    val contentManager = ProjectLevelVcsManagerEx.getInstanceEx(project).contentManager!!
    ContentUtilEx.addTabbedContent(contentManager, panel, "Update Info", DateFormatUtil.formatDateTime(System.currentTimeMillis()),
                                   select, panel.getUi())
    if (select) {
      ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS).activate(null)
    }
    return logUi
  }

  private inner class MyLogUiFactory(val logManager: VcsLogManager,
                                     val rangeFilter: VcsLogRangeFilter,
                                     val listenerGetter: ((VcsLogUiImpl, MyLogUiFactory) -> VisiblePackChangeListener)?)
    : VcsLogManager.VcsLogUiFactory<VcsLogUiImpl> {
    override fun createLogUi(project: Project, logData: VcsLogData): VcsLogUiImpl {
      val logId = "git-update-project-info-" + UUID.randomUUID()
      val properties = MyPropertiesForRange(rangeFilter, project.service<GitUpdateProjectInfoLogProperties>())

      val vcsLogFilterer = VcsLogFiltererImpl(logData.logProviders, logData.storage, logData.topCommitsCache, logData.commitDetailsGetter,
                                              logData.index)
      val initialSortType = properties.get<PermanentGraph.SortType>(MainVcsLogUiProperties.BEK_SORT_TYPE)
      val refresher = VisiblePackRefresherImpl(project, logData, VcsLogFilterObject.collection(rangeFilter), initialSortType,
                                               vcsLogFilterer, logId)

      // null for initial filters means that filters will be loaded from properties: saved filters + the range filter which we've just set
      val logUi = VcsLogUiImpl(logId, logData, logManager.colorManager, properties, refresher, null)

      if (listenerGetter != null) {
        refresher.addVisiblePackChangeListener(listenerGetter.invoke(logUi, this))
      }

      return logUi
    }
  }

  private class MyPropertiesForRange(val rangeFilter: VcsLogRangeFilter,
                                     val mainProperties: GitUpdateProjectInfoLogProperties) : MainVcsLogUiProperties by mainProperties {
    private val filters = mutableMapOf<String, List<String>>()
    private var explicitlyRemovedPathsFilter = false

    override fun getFilterValues(filterName: String): List<String>? {
      when (filterName) {
        RANGE_FILTER.name -> return ArrayList(rangeFilter.getTextPresentation())
        STRUCTURE_FILTER.name, ROOT_FILTER.name -> {
          if (explicitlyRemovedPathsFilter) return null
          return filters[filterName] ?: mainProperties.getFilterValues(filterName)
        }
        else -> return filters[filterName]
      }
    }

    override fun saveFilterValues(filterName: String, values: List<String>?) {
      if (values != null) {
        filters[filterName] = values
      }
      else {
        filters.remove(filterName)
      }

      if (filterName == STRUCTURE_FILTER.name || filterName == ROOT_FILTER.name) {
        explicitlyRemovedPathsFilter = values == null
      }
    }
  }

  private fun calcUpdatedCommitsCount(): Int {
    return calcCount { repository, range ->
      GitHistoryUtils.collectTimedCommits(project, repository.root,
                                          "${range.start.asString()}..${range.end.asString()}").size
    }
  }

  private fun calcUpdatedFilesCount(): Int {
    return calcCount { repository, range ->
      MergeChangeCollector(project, repository.root, GitRevisionNumber(range.start.asString())).calcUpdatedFilesCount()
    }
  }

  private fun calcCount(sizeForRepo: (GitRepository, HashRange) -> Int): Int {
    var result = 0
    for ((repository, range) in ranges) {
      try {
        result += sizeForRepo(repository, range)
      }
      catch (e: VcsException) {
        LOG.warn("Couldn't collect commits in root ${repository.root} in range $range", e)
      }
    }
    return result
  }

  private inner class MyVisiblePackChangeListener(val logUi: VcsLogUiImpl,
                                                  val factory: MyLogUiFactory,
                                                  val commitsAndFiles: CommitsAndFiles,
                                                  val dataSupplier: CompletableFuture<NotificationData>) : VisiblePackChangeListener {

    override fun onVisiblePackChange(visiblePack: VisiblePack) {
      runInEdt {
        if (!dataSupplier.isDone && areFiltersEqual(visiblePack.filters, logUi.filterUi.filters)) {
          logUi.refresher.removeVisiblePackChangeListener(this)

          val visibleCommitCount = visiblePack.visibleGraph.visibleCommitCount
          val data = NotificationData(commitsAndFiles.updatedFilesCount, commitsAndFiles.receivedCommitsCount, visibleCommitCount,
                                      getViewCommitsAction(logUi, factory))
          dataSupplier.complete(data)
        }
      }
    }
  }
}

private fun areFiltersEqual(filters1: VcsLogFilterCollection, filters2: VcsLogFilterCollection) : Boolean {
  if (filters1 === filters2) return true
  if (filters1.filters.size != filters2.filters.size) return false
  return filters1.filters.all { it == filters2.get(it.key) }
}