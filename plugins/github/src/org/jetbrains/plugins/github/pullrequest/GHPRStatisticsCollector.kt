// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.collaboration.async.disposingScope
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHEnterpriseServerMeta
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListSearchValue
import org.jetbrains.plugins.github.util.GHEnterpriseServerMetadataLoader
import java.util.*

internal object GHPRStatisticsCollector {
  private val COUNTERS_GROUP = EventLogGroup("vcs.github.pullrequest.counters", 4)

  class Counters : CounterUsagesCollector() {
    override fun getGroup() = COUNTERS_GROUP
  }

  private val SELECTORS_OPENED_EVENT = COUNTERS_GROUP.registerEvent("selectors.opened")
  private val LIST_OPENED_EVENT = COUNTERS_GROUP.registerEvent("list.opened")

  private val FILTER_SEARCH_PRESENT = EventFields.Boolean("hasSearch")
  private val FILTER_STATE_PRESENT = EventFields.Boolean("hasState")
  private val FILTER_AUTHOR_PRESENT = EventFields.Boolean("hasAuthor")
  private val FILTER_ASSIGNEE_PRESENT = EventFields.Boolean("hasAssignee")
  private val FILTER_REVIEW_PRESENT = EventFields.Boolean("hasReviewState")
  private val FILTER_LABEL_PRESENT = EventFields.Boolean("hasLabel")

  private val FILTERS_APPLIED_EVENT = COUNTERS_GROUP.registerVarargEvent("list.filters.applied",
                                                                         FILTER_SEARCH_PRESENT,
                                                                         FILTER_STATE_PRESENT,
                                                                         FILTER_AUTHOR_PRESENT,
                                                                         FILTER_ASSIGNEE_PRESENT,
                                                                         FILTER_REVIEW_PRESENT,
                                                                         FILTER_LABEL_PRESENT)

  private val DETAILS_OPENED_EVENT = COUNTERS_GROUP.registerEvent("details.opened")
  private val NEW_OPENED_EVENT = COUNTERS_GROUP.registerEvent("new.pr.view.opened")

  private val TIMELINE_OPENED_EVENT = COUNTERS_GROUP.registerEvent("timeline.opened", EventFields.Int("count"))
  private val DIFF_OPENED_EVENT = COUNTERS_GROUP.registerEvent("diff.opened", EventFields.Int("count"))
  private val MERGED_EVENT = COUNTERS_GROUP.registerEvent("merged", EventFields.Enum<GithubPullRequestMergeMethod>("method") {
    it.name.uppercase(Locale.getDefault())
  })
  private val anonymizedId = object : PrimitiveEventField<String>() {

    override val name = "anonymized_id"

    override fun addData(fuData: FeatureUsageData, value: String) {
      fuData.addAnonymizedId(value)
    }

    override val validationRule: List<String>
      get() = listOf("{regexp#hash}")
  }
  private val SERVER_META_EVENT = COUNTERS_GROUP.registerEvent("server.meta.collected", anonymizedId, EventFields.Version)

  private val DETAILS_BRANCHES_EVENT = COUNTERS_GROUP.registerEvent("details.branches.opened")
  private val DETAILS_BRANCH_CHECKED_OUT_EVENT = COUNTERS_GROUP.registerEvent("details.branch.checked.out")
  private val DETAILS_COMMIT_CHOSEN_EVENT = COUNTERS_GROUP.registerEvent("details.commit.chosen")
  private val DETAILS_NEXT_COMMIT_EVENT = COUNTERS_GROUP.registerEvent("details.next.commit.chosen")
  private val DETAILS_PREV_COMMIT_EVENT = COUNTERS_GROUP.registerEvent("details.prev.commit.chosen")
  private val DETAILS_CHANGE_EVENT = COUNTERS_GROUP.registerEvent("details.change.selected")
  private val DETAILS_CHECKS_EVENT = COUNTERS_GROUP.registerEvent("details.checks.opened")


  private val DETAILS_ACTION_EVENT = COUNTERS_GROUP.registerEvent("details.additional.actions.invoked",
                                                                  EventFields.Enum("action", GHPRAction::class.java),
                                                                  EventFields.Boolean("isDefault"))

  fun logSelectorsOpened() {
    SELECTORS_OPENED_EVENT.log()
  }

  fun logListOpened() {
    LIST_OPENED_EVENT.log()
  }

  fun logListFiltersApplied(filters: GHPRListSearchValue): Unit =
    FILTERS_APPLIED_EVENT.log(
      EventPair(FILTER_SEARCH_PRESENT, filters.searchQuery != null),
      EventPair(FILTER_STATE_PRESENT, filters.state != null),
      EventPair(FILTER_AUTHOR_PRESENT, filters.author != null),
      EventPair(FILTER_ASSIGNEE_PRESENT, filters.assignee != null),
      EventPair(FILTER_REVIEW_PRESENT, filters.reviewState != null),
      EventPair(FILTER_LABEL_PRESENT, filters.label != null)
    )

  fun logDetailsOpened() {
    DETAILS_OPENED_EVENT.log()
  }

  fun logNewPRViewOpened() {
    NEW_OPENED_EVENT.log()
  }

  fun logTimelineOpened(project: Project) {
    val count = FileEditorManager.getInstance(project).openFiles.count { it is GHPRTimelineVirtualFile }
    TIMELINE_OPENED_EVENT.log(project, count)
  }

  fun logDiffOpened(project: Project) {
    val count = FileEditorManager.getInstance(project).openFiles.count {
      it is GHPRDiffVirtualFileBase
      || it is GHPRCombinedDiffPreviewVirtualFileBase
    }
    DIFF_OPENED_EVENT.log(project, count)
  }

  fun logDetailsBranchesOpened() {
    DETAILS_BRANCHES_EVENT.log()
  }

  fun logDetailsBranchCheckedOut() {
    DETAILS_BRANCH_CHECKED_OUT_EVENT.log()
  }

  fun logDetailsCommitChosen() {
    DETAILS_COMMIT_CHOSEN_EVENT.log()
  }

  fun logDetailsNextCommitChosen() {
    DETAILS_NEXT_COMMIT_EVENT.log()
  }

  fun logDetailsPrevCommitChosen() {
    DETAILS_PREV_COMMIT_EVENT.log()
  }

  fun logDetailsChecksOpened() {
    DETAILS_CHECKS_EVENT.log()
  }

  fun logDetailsActionInvoked(action: GHPRAction, isDefault: Boolean) {
    DETAILS_ACTION_EVENT.log(action, isDefault)
  }

  fun logChangeSelected() {
    DETAILS_CHANGE_EVENT.log()
  }

  fun logMergedEvent(method: GithubPullRequestMergeMethod) {
    MERGED_EVENT.log(method)
  }

  fun logEnterpriseServerMeta(project: Project, server: GithubServerPath, meta: GHEnterpriseServerMeta) {
    SERVER_META_EVENT.log(project, server.toUrl(), meta.installedVersion)
  }
}

enum class GHPRAction {
  REQUEST_REVIEW,
  REQUEST_REVIEW_MYSELF,
  RE_REQUEST_REVIEW,
  CLOSE,
  REOPEN,
  POST_REVIEW,
  MERGE,
  SQUASH_MERGE,
  REBASE_MERGE
}

@Service
private class GHServerVersionsCollector(private val project: Project) : Disposable {

  private val scope = disposingScope()

  init {
    val accountsFlow = project.service<GHAccountManager>().accountsState
    scope.launch {
      accountsFlow.collect {
        for (account in it) {
          val server = account.server
          if (server.isGithubDotCom) continue

          //TODO: load with auth to avoid rate-limit
          try {
            val metadata = service<GHEnterpriseServerMetadataLoader>().loadMetadata(server).await()
            GHPRStatisticsCollector.logEnterpriseServerMeta(project, server, metadata)
          }
          catch (ignore: Exception) {
          }
        }
      }
    }
  }

  class Initializer : ProjectActivity {
    override suspend fun execute(project: Project) {
      // init service to start version checks
      project.service<GHServerVersionsCollector>()
    }
  }

  override fun dispose() = Unit
}
