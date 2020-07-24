// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.Consumer
import com.intellij.util.ContentUtilEx
import com.intellij.util.ui.StatusText
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.VcsLogRootFilter
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.impl.createAndOpenLogFile
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.visible.VcsLogFiltererImpl
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.collection
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromRange
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromRoot
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import java.util.*

internal class GitCompareBranchesUi @JvmOverloads constructor(private val project: Project,
                                                              private val repositories: List<GitRepository>,
                                                              private val branchName: String,
                                                              private val otherBranchName: String = "") {


  fun create() {
    VcsProjectLog.runWhenLogIsReady(project) { _, logManager ->
      val oneRepo = repositories.size == 1
      val firstRepo = repositories[0]
      val currentBranchName = firstRepo.currentBranchName
      val secondRef = when {
        otherBranchName.isNotBlank() -> otherBranchName
        oneRepo && !currentBranchName.isNullOrBlank() -> currentBranchName
        else -> "HEAD"
      }

      val rangeFilter = fromRange(secondRef, branchName)
      val rootFilter = if (oneRepo) fromRoot(firstRepo.root) else null

      createCompareBranchesUi(logManager, rangeFilter, rootFilter, secondRef)
    }
  }

  private fun createCompareBranchesUi(logManager: VcsLogManager,
                                      rangeFilter: VcsLogRangeFilter,
                                      rootFilter: VcsLogRootFilter?,
                                      secondRef: String) {
    val tabName = getEditorTabName(branchName, secondRef)

    val topLogUiFactory = MyLogUiFactory("git-compare-branches-top-" + UUID.randomUUID(),
                                         MyPropertiesForHardcodedFilters(project.service<GitCompareBranchesTopLogProperties>()),
                                         logManager, rangeFilter, rootFilter)
    val bottomLogUiFactory = MyLogUiFactory("git-compare-branches-bottom-" + UUID.randomUUID(),
                                            MyPropertiesForHardcodedFilters(project.service<GitCompareBranchesBottomLogProperties>()),
                                            logManager, rangeFilter.asReversed(), rootFilter)
    val topLogUi = logManager.createLogUi(topLogUiFactory, VcsLogManager.LogWindowKind.EDITOR)
    val bottomLogUi = logManager.createLogUi(bottomLogUiFactory, VcsLogManager.LogWindowKind.EDITOR)

    val mainSplitter = OnePixelSplitter(true).apply {
      firstComponent = VcsLogPanel(logManager, topLogUi)
      secondComponent = VcsLogPanel(logManager, bottomLogUi)
    }
    createAndOpenLogFile(project, logManager, mainSplitter, listOf(topLogUi, bottomLogUi), tabName, { tabName }, true)
  }

  private fun getEditorTabName(branch1Name: String, branch2Name: String) =
    ContentUtilEx.getFullName(GitBundle.message("git.compare.branches.tab.name"),
                              StringUtil.shortenTextWithEllipsis(
                                GitBundle.message("git.compare.branches.tab.suffix", branch1Name, branch2Name), 150, 20))

  private class MyLogUiFactory(val logId: String,
                               val properties: MainVcsLogUiProperties,
                               val logManager: VcsLogManager,
                               val rangeFilter: VcsLogRangeFilter,
                               val rootFilter: VcsLogRootFilter?) : VcsLogManager.VcsLogUiFactory<MainVcsLogUi> {
    override fun createLogUi(project: Project, logData: VcsLogData): MainVcsLogUi {
      val vcsLogFilterer = VcsLogFiltererImpl(logData.logProviders, logData.storage, logData.topCommitsCache, logData.commitDetailsGetter,
                                              logData.index)
      val initialSortType = properties.get<PermanentGraph.SortType>(MainVcsLogUiProperties.BEK_SORT_TYPE)
      val refresher = VisiblePackRefresherImpl(project, logData, collection(), initialSortType, vcsLogFilterer, logId)

      return MyVcsLogUi(logId, logData, logManager.colorManager, properties, refresher, rangeFilter, rootFilter)
    }
  }

  private class MyVcsLogUi(id: String, logData: VcsLogData, colorManager: VcsLogColorManager,
                           uiProperties: MainVcsLogUiProperties, refresher: VisiblePackRefresher,
                           rangeFilter: VcsLogRangeFilter, rootFilter: VcsLogRootFilter?) :
    VcsLogUiImpl(id, logData, colorManager, uiProperties, refresher, collection(rangeFilter, rootFilter)) {

    override fun createFilterUi(filterConsumer: Consumer<VcsLogFilterCollection>,
                                filters: VcsLogFilterCollection?,
                                parentDisposable: Disposable): VcsLogFilterUiEx {
      return MyFilterUi(logData, filterConsumer, properties, colorManager, filters, parentDisposable)
    }

    override fun applyFiltersAndUpdateUi(filters: VcsLogFilterCollection) {
      super.applyFiltersAndUpdateUi(filters)
      val (start, end) = filters.get(VcsLogFilterCollection.RANGE_FILTER).getRange()
      mainFrame.setExplanationHtml(getExplanationText(start, end))
    }
  }

  private class MyFilterUi(data: VcsLogData, filterConsumer: Consumer<VcsLogFilterCollection>, properties: MainVcsLogUiProperties,
                           colorManager: VcsLogColorManager, filters: VcsLogFilterCollection?, parentDisposable: Disposable
  ) : VcsLogClassicFilterUi(data, filterConsumer, properties, colorManager, filters, parentDisposable) {

    val rangeFilter: VcsLogRangeFilter
      get() = myBranchFilterModel.rangeFilter!!

    override fun createBranchComponent(): FilterActionComponent? = null

    override fun setCustomEmptyText(text: StatusText) {
      if (filters.filters.any { it !is VcsLogRangeFilter && it !is VcsLogRootFilter }) {
        // additional filters have been set => display the generic message
        super.setCustomEmptyText(text)
      }
      else {
        val (start, end) = rangeFilter.getRange()
        text.text = GitBundle.message("git.compare.branches.empty.status", start, end)
      }
    }

    override fun setFilters(collection: VcsLogFilterCollection) {
      if (collection.isEmpty) {
        if (myStructureFilterModel.structureFilter != null) myStructureFilterModel.setFilter(null)
        myDateFilterModel.setFilter(null)
        myTextFilterModel.setFilter(null)
        myUserFilterModel.setFilter(null)
      }
      else {
        collection.get(VcsLogFilterCollection.RANGE_FILTER)?.let(myBranchFilterModel::setRangeFilter)
      }
    }
  }

  private class MyPropertiesForHardcodedFilters(
    mainProperties: GitCompareBranchesLogProperties
  ) : MainVcsLogUiProperties by mainProperties {

    private val filters = mutableMapOf<String, List<String>>()

    override fun getFilterValues(filterName: String): List<String>? {
      return filters[filterName]
    }

    override fun saveFilterValues(filterName: String, values: List<String>?) {
      if (values != null) {
        filters[filterName] = values
      }
      else {
        filters.remove(filterName)
      }
    }
  }
}

private fun VcsLogRangeFilter?.getRange(): VcsLogRangeFilter.RefRange {
  check(this != null && ranges.size == 1) {
    "At this point there is one and only one range filter, changing it from the UI is disabled"
  }
  return ranges[0]
}

private fun VcsLogRangeFilter.asReversed(): VcsLogRangeFilter {
  val (start, end) = getRange()
  return fromRange(end, start)
}

private fun getExplanationText(dontExist: String, existIn: String): String =
  "<html>${GitBundle.message("git.compare.branches.explanation.message",
                             "<code><b>$existIn</b></code>", "<code><b>$dontExist</b></code>")}</html>"
