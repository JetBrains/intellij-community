// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update

import com.intellij.notification.Notification
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.ui.GuiUtils
import com.intellij.util.ContentUtilEx
import com.intellij.util.text.DateFormatUtil
import com.intellij.vcs.log.VcsLogFilterCollection.RANGE_FILTER
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.utils.DfsWalk
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogContentUtil
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.findBranch
import com.intellij.vcs.log.visible.VcsLogFiltererImpl
import com.intellij.vcs.log.visible.VisiblePack
import com.intellij.vcs.log.visible.VisiblePackChangeListener
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import git4idea.changes.GitChangeUtils
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepository
import org.jetbrains.annotations.CalledInAwt
import java.util.*
import kotlin.collections.ArrayList

private val LOG = logger<GitUpdateInfoAsLog>()

class GitUpdateInfoAsLog(private val project: Project,
                         private val ranges: Map<GitRepository, HashRange>,
                         private val notificationProducer: (Int, Int, Int?, Runnable) -> Notification) {

  private var notificationShown: Boolean = false // accessed only from EDT

  @CalledInAwt
  fun buildAndShowNotification() {
    notificationShown = false
    val currentBranches = ranges.mapValues { (repo, _) -> repo.currentBranchName }
    VcsLogUtil.runWhenLogIsReady(project) { log, logManager ->
      val listener = object : DataPackChangeListener {
        override fun onDataPackChange(dataPack: DataPack) {
          showNotificationIfRangesAreReachable(log, dataPack, logManager, this, currentBranches)
        }
      }

      log.dataManager?.addDataPackChangeListener(listener)

      GuiUtils.invokeLaterIfNeeded({
        // the log may be refreshed before we subscribe to the listener
        showNotificationIfRangesAreReachable(log, logManager.dataManager.dataPack, logManager, listener, currentBranches)
      }, ModalityState.defaultModalityState())
    }
  }

  @CalledInAwt
  private fun showNotificationIfRangesAreReachable(log: VcsProjectLog,
                                                   dataPack: DataPack,
                                                   logManager: VcsLogManager,
                                                   listener: DataPackChangeListener,
                                                   currentBranches: Map<GitRepository, String?>) {
    if (!notificationShown && areRangesReachableFromCurrentHead(log, dataPack, currentBranches)) {
      notificationShown = true
      log.dataManager?.removeDataPackChangeListener(listener)
      showTabAndNotificationAfterCalculations(logManager)
    }
  }

  private fun areRangesReachableFromCurrentHead(log: VcsProjectLog,
                                                dataPack: DataPack,
                                                currentBranches: Map<GitRepository, String?>): Boolean {
    return ranges.all { (repository, range) ->
      val currentBranch = currentBranches[repository]
      if (currentBranch == null) return@all false
      val ref = dataPack.refsModel.findBranch(currentBranch, repository.root)
      if (ref == null) return@all false
      val endOfRange = range.end

      val storage = log.dataManager!!.storage
      val rangeIndex = storage.getCommitIndex(endOfRange, repository.root)
      val headIndex = storage.getCommitIndex(ref.commitHash, ref.root)

      val permanentGraph = dataPack.permanentGraph as PermanentGraphImpl<Int>
      val headNodeId = permanentGraph.permanentCommitsInfo.getNodeId(headIndex)
      val rangeNodeId = permanentGraph.permanentCommitsInfo.getNodeId(rangeIndex)

      var found = false
      DfsWalk(listOf(headNodeId), permanentGraph.linearGraph).walk(true) { node: Int ->
        if (node == rangeNodeId) {
          found = true
          false
        }
        else {
          true
        }
      }
      found
    }
  }

  private fun showTabAndNotificationAfterCalculations(logManager: VcsLogManager) {
    ApplicationManager.getApplication().executeOnPooledThread {
      val updatedCommitsCount = calcUpdatedCommitsCount()
      val updatedFilesCount = calcUpdatedFilesCount()

      ApplicationManager.getApplication().invokeLater {
        val rangeFilter = VcsLogFilterObject.fromRange(ranges.values.map {
          VcsLogRangeFilter.RefRange(it.start.asString(), it.end.asString())
        })
        val logUiFactory = MyLogUiFactory(logManager, rangeFilter, updatedFilesCount, updatedCommitsCount)
        val logUi = logManager.createLogUi(logUiFactory, true)
        val panel = VcsLogPanel(logManager, logUi)
        val contentManager = ProjectLevelVcsManagerEx.getInstanceEx(project).contentManager!!
        ContentUtilEx.addTabbedContent(contentManager, panel, "Update Info", DateFormatUtil.formatDateTime(System.currentTimeMillis()),
                                       false, panel.getUi())
      }
    }
  }

  private inner class MyLogUiFactory(val logManager: VcsLogManager,
                                      val rangeFilter: VcsLogRangeFilter,
                                      val updatedFilesCount: Int,
                                      val updateCommitsCount: Int) : VcsLogManager.VcsLogUiFactory<VcsLogUiImpl> {

    override fun createLogUi(project: Project, logData: VcsLogData): VcsLogUiImpl {
      val logId = "git-update-project-info-" + UUID.randomUUID()
      val properties = MyPropertiesForRange(rangeFilter, project.service<GitUpdateProjectInfoLogProperties>())

      val vcsLogFilterer = VcsLogFiltererImpl(logData.logProviders, logData.storage, logData.topCommitsCache, logData.commitDetailsGetter,
                                              logData.index)
      val initialSortType = properties.get<PermanentGraph.SortType>(MainVcsLogUiProperties.BEK_SORT_TYPE)
      val refresher = VisiblePackRefresherImpl(project, logData, VcsLogFilterObject.collection(), initialSortType, vcsLogFilterer, logId)

      // null for initial filters means that filters will be loaded from properties: saved filters + the range filter which we've just set
      val logUi = VcsLogUiImpl(logId, logData, logManager.colorManager, properties, refresher, null)

      val listener = MyVisiblePackChangeListener(logUi, updatedFilesCount, updateCommitsCount, properties.havePresetFilters())
      refresher.addVisiblePackChangeListener(listener)
      return logUi
    }
  }

  private class MyPropertiesForRange(val rangeFilter: VcsLogRangeFilter,
                                      val mainProperties: GitUpdateProjectInfoLogProperties) : MainVcsLogUiProperties by mainProperties {
    override fun getFilterValues(filterName: String): List<String>? {
      if (filterName === RANGE_FILTER.name) {
        return ArrayList(rangeFilter.getTextPresentation())
      }
      else {
        return mainProperties.getFilterValues(filterName)
      }
    }

    fun havePresetFilters(): Boolean {
      val filters = mainProperties.state.FILTERS
      return if (filters[RANGE_FILTER.name] != null) filters.size > 1 else filters.isNotEmpty()
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
      GitChangeUtils.getDiffWithWorkingDir(project, repository.root, range.start.asString(), null, false, false).size
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


  inner class MyVisiblePackChangeListener(private val logUi: VcsLogUiImpl,
                                          private val updatedFilesCount: Int,
                                          private val updatedCommitsCount: Int,
                                          private val thereArePresetFilters: Boolean) : VisiblePackChangeListener {

    private var visibleCommitCount: Int = -1

    override fun onVisiblePackChange(visiblePack: VisiblePack) {
      runInEdt {
        if (visibleCommitCount < 0) { // make sure the code is executed only once in case of two asynchronous VisiblePack updates
          visibleCommitCount = visiblePack.visibleGraph.visibleCommitCount

          val filteredCommitsNumber = if (thereArePresetFilters) visibleCommitCount else null
          val notification = notificationProducer(updatedFilesCount, updatedCommitsCount, filteredCommitsNumber,
                                                  Runnable { VcsLogContentUtil.selectLogUi(project, logUi) })
          VcsNotifier.getInstance(project).notify(notification)

          logUi.refresher.removeVisiblePackChangeListener(this)
        }
      }
    }
  }
}