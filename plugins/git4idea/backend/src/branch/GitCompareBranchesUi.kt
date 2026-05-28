// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.OnePixelSplitter
import com.intellij.util.ContentUtilEx
import com.intellij.util.ui.StatusText
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.VcsLogRootFilter
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogManager
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
import git4idea.i18n.GitBundleExtensions.html
import git4idea.repo.GitRepository
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.UUID
import java.util.function.Consumer
import javax.swing.JComponent

@ApiStatus.Internal
class GitCompareBranchesUi(
  private val project: Project,
  val rangeFilter: VcsLogRangeFilter,
  internal val rootFilter: VcsLogRootFilter?,
) {
  constructor(
    project: Project,
    repositories: List<GitRepository>,
    branchName: String,
    otherBranchName: String,
  ) : this(project,
           fromRange(otherBranchName, branchName),
           repositories.singleOrNull()?.let { fromRoot(it.root) })

  internal fun create(logManager: VcsLogManager, onDispose: () -> Unit): JComponent {
    val topLogUi = createTopUi(logManager).also {
      Disposer.register(it) {
        onDispose()
      }
    }
    val bottomLogUi = createBottomUi(logManager).also {
      Disposer.register(it) {
        onDispose()
      }
    }
    return OnePixelSplitter(true).apply {
      firstComponent = VcsLogPanel(logManager, topLogUi)
      secondComponent = VcsLogPanel(logManager, bottomLogUi)
    }
  }

  fun createTopUi(logManager: VcsLogManager, isEditorDiffPreview: Boolean = true): MainVcsLogUi {
    val topLogUiFactory = MyLogUiFactory("git-compare-branches-top-" + UUID.randomUUID(),
                                         MyPropertiesForHardcodedFilters(project.service<GitCompareBranchesTopLogProperties>()),
                                         logManager.colorManager, rangeFilter, rootFilter, isEditorDiffPreview)
    return logManager.createLogUi(topLogUiFactory)
  }

  fun createBottomUi(logManager: VcsLogManager, isEditorDiffPreview: Boolean = true): MainVcsLogUi {
    val bottomLogUiFactory = MyLogUiFactory("git-compare-branches-bottom-" + UUID.randomUUID(),
                                            MyPropertiesForHardcodedFilters(project.service<GitCompareBranchesBottomLogProperties>()),
                                            logManager.colorManager, rangeFilter.asReversed(), rootFilter, isEditorDiffPreview)
    return logManager.createLogUi(bottomLogUiFactory)
  }

  private class MyLogUiFactory(val logId: String,
                               val properties: MainVcsLogUiProperties,
                               val colorManager: VcsLogColorManager,
                               val rangeFilter: VcsLogRangeFilter,
                               val rootFilter: VcsLogRootFilter?,
                               private val isEditorDiffPreview: Boolean) : VcsLogManager.VcsLogUiFactory<MainVcsLogUi> {
    override fun createLogUi(project: Project, logData: VcsLogData): MainVcsLogUi {
      val vcsLogFilterer = VcsLogFiltererImpl(logData)
      val initialGraphOptions = properties[MainVcsLogUiProperties.GRAPH_OPTIONS]
      val refresher = VisiblePackRefresherImpl(project, logData, collection(), initialGraphOptions, vcsLogFilterer, logId)

      return MyVcsLogUi(logId, logData, colorManager, properties, refresher, rangeFilter, rootFilter, isEditorDiffPreview)
    }
  }

  internal class MyVcsLogUi(id: String, logData: VcsLogData, colorManager: VcsLogColorManager,
                           uiProperties: MainVcsLogUiProperties, refresher: VisiblePackRefresher,
                           rangeFilter: VcsLogRangeFilter, rootFilter: VcsLogRootFilter?,
                           isEditorDiffPreview: Boolean) :
    VcsLogUiImpl(id, logData, colorManager, uiProperties, refresher, collection(rangeFilter, rootFilter), isEditorDiffPreview) {

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
      get() = branchFilterModel.rangeFilter!!

    override fun createBranchComponent() = null

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
        if (structureFilterModel.structureFilter != null) structureFilterModel.setFilter(null)
        dateFilterModel.setFilter(null)
        textFilterModel.setFilter(null)
        userFilterModel.setFilter(null)
      }
      else {
        collection.get(VcsLogFilterCollection.RANGE_FILTER)?.let { branchFilterModel.rangeFilter = it }
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

@ApiStatus.Internal
fun getEditorTabName(rangeFilter: VcsLogRangeFilter): @Nls String {
  val (start, end) = rangeFilter.getRange()
  return ContentUtilEx.getFullName(GitBundle.message("git.compare.branches.tab.name"),
                                   StringUtil.shortenTextWithEllipsis(GitBundle.message("git.compare.branches.tab.suffix", end, start),
                                                                      150, 20))
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

@NlsContexts.LinkLabel
private fun getExplanationText(@NlsSafe dontExist: String, @NlsSafe existIn: String): String {
  return html("git.compare.branches.explanation.message",
              HtmlChunk.tag("code").child(HtmlChunk.text(existIn).bold()),
              HtmlChunk.tag("code").child(HtmlChunk.text(dontExist).bold()))
}
