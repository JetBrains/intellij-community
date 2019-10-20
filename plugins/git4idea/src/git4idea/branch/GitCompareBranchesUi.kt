// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.branch

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.Consumer
import com.intellij.util.ContentUtilEx
import com.intellij.util.ui.StatusText
import com.intellij.vcs.log.VcsLogFilter
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.VcsLogRootFilter
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogManager
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogPanel
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.util.VcsLogUiUtil.getLinkAttributes
import com.intellij.vcs.log.visible.VcsLogFiltererImpl
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.collection
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromRange
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromRoot
import git4idea.repo.GitRepository
import java.util.*

class GitCompareBranchesUi(private val project: Project, private val repositories: List<GitRepository>, private val branchName: String) {

  fun create() {
    VcsProjectLog.runWhenLogIsReady(project) { _, logManager ->
      val oneRepo = repositories.size == 1
      val firstRepo = repositories[0]
      val currentBranchName = firstRepo.currentBranchName
      val currentRef = if (oneRepo && currentBranchName != null) currentBranchName else "HEAD"

      val rangeFilter = fromRange(currentRef, branchName)
      val rootFilter = if (oneRepo) fromRoot(firstRepo.root) else null

      createLogUiAndTab(logManager, MyLogUiFactory(logManager, rangeFilter, rootFilter), currentRef)
    }
  }

  private fun createLogUiAndTab(logManager: VcsLogManager, logUiFactory: MyLogUiFactory, currentRef: String) {
    val logUi = logManager.createLogUi(logUiFactory, true)
    val panel = VcsLogPanel(logManager, logUi)
    val contentManager = ProjectLevelVcsManagerEx.getInstanceEx(project).contentManager!!
    ContentUtilEx.addTabbedContent(contentManager, panel, "Compare", "$branchName and $currentRef", true, panel.getUi())
    ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.VCS).activate(null)
  }

  private class MyLogUiFactory(val logManager: VcsLogManager,
                               val rangeFilter: VcsLogRangeFilter,
                               val rootFilter: VcsLogRootFilter?) : VcsLogManager.VcsLogUiFactory<VcsLogUiImpl> {
    override fun createLogUi(project: Project, logData: VcsLogData): VcsLogUiImpl {
      val logId = "git-compare-branches-" + UUID.randomUUID()
      val properties = MyPropertiesForHardcodedFilters(project.service<GitCompareBranchesLogProperties>())

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
      mainFrame.setExplanationHtml(getExplanationText(start, end));
    }
  }

  private class MyFilterUi(data: VcsLogData, filterConsumer: Consumer<VcsLogFilterCollection>, properties: MainVcsLogUiProperties,
                           colorManager: VcsLogColorManager, filters: VcsLogFilterCollection?, parentDisposable: Disposable
  ) : VcsLogClassicFilterUi(data, filterConsumer, properties, colorManager, filters, parentDisposable) {

    private val rangeFilter: VcsLogRangeFilter
      get() = myBranchFilterModel.rangeFilter!!

    override fun createBranchComponent(): FilterActionComponent {
      return FilterActionComponent {
        LinkLabel.create("Swap Branches") {
          setFilter(rangeFilter.asReversed())
        }
      }
    }

    override fun setCustomEmptyText(text: StatusText) {
      if (filters.filters.any { it !is VcsLogRangeFilter && it !is VcsLogRootFilter }) {
        // additional filters have been set => display the generic message
        super.setCustomEmptyText(text)
      }
      else {
        val (start, end) = rangeFilter.getRange()
        text.text = "$start contains all commits from $end"
        text.appendSecondaryText("Swap Branches", getLinkAttributes()) {
          setFilter(rangeFilter.asReversed())
        }
      }
    }

    override fun setFilter(filter: VcsLogFilter?) {
      when (filter) {
        null -> {
          if (myStructureFilterModel.structureFilter != null) myStructureFilterModel.setFilter(null)
          myDateFilterModel.setFilter(null)
          myTextFilterModel.setFilter(null)
          myUserFilterModel.setFilter(null)
        }
        is VcsLogRangeFilter -> myBranchFilterModel.setRangeFilter(filter)
      }
    }
  }

  private class MyPropertiesForHardcodedFilters(
    val mainProperties: GitCompareBranchesLogProperties
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
  "<html>Commits that exist in <code><b>$existIn</b></code> but don't exist in <code><b>$dontExist</b></code></html>"
