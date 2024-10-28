// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHEnterpriseServerMeta
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListSearchValue
import org.jetbrains.plugins.github.util.GHEnterpriseServerMetadataLoader
import java.util.*

internal object GHPRStatisticsCollector: CounterUsagesCollector() {
  private val COUNTERS_GROUP = EventLogGroup("vcs.github.pullrequest.counters", 7)

  override fun getGroup() = COUNTERS_GROUP

  private val SELECTORS_OPENED_EVENT = COUNTERS_GROUP.registerEvent("selectors.opened")
  private val LIST_OPENED_EVENT = COUNTERS_GROUP.registerEvent("list.opened")

  private val FILTER_SEARCH_PRESENT = EventFields.Boolean("has_search")
  private val FILTER_STATE_PRESENT = EventFields.Boolean("has_state")
  private val FILTER_AUTHOR_PRESENT = EventFields.Boolean("has_author")
  private val FILTER_ASSIGNEE_PRESENT = EventFields.Boolean("has_assignee")
  private val FILTER_REVIEW_PRESENT = EventFields.Boolean("has_review_state")
  private val FILTER_LABEL_PRESENT = EventFields.Boolean("has_label")
  private val FILTER_SORT_PRESENT = EventFields.Boolean("has_sort")

  private val FILTERS_APPLIED_EVENT = COUNTERS_GROUP.registerVarargEvent("list.filters.applied",
                                                                         FILTER_SEARCH_PRESENT,
                                                                         FILTER_STATE_PRESENT,
                                                                         FILTER_AUTHOR_PRESENT,
                                                                         FILTER_ASSIGNEE_PRESENT,
                                                                         FILTER_REVIEW_PRESENT,
                                                                         FILTER_LABEL_PRESENT,
                                                                         FILTER_SORT_PRESENT)

  private val DETAILS_OPENED_EVENT = COUNTERS_GROUP.registerEvent("details.opened")
  private val NEW_OPENED_EVENT = COUNTERS_GROUP.registerEvent("new.pr.view.opened")

  private val TIMELINE_OPENED_EVENT = COUNTERS_GROUP.registerEvent("timeline.opened", EventFields.Int("count"))
  private val DIFF_OPENED_EVENT = COUNTERS_GROUP.registerEvent("diff.opened", EventFields.Int("count"))
  private val MERGED_EVENT = COUNTERS_GROUP.registerEvent("merged", EventFields.Enum<GithubPullRequestMergeMethod>("method") {
    it.name.uppercase(Locale.getDefault())
  })
  private val anonymizedId = EventFields.AnonymizedId

  private val SERVER_META_EVENT = COUNTERS_GROUP.registerEvent("server.meta.collected", anonymizedId, EventFields.Version)

  private val DETAILS_BRANCHES_EVENT = COUNTERS_GROUP.registerEvent("details.branches.opened")
  private val DETAILS_BRANCH_CHECKED_OUT_EVENT = COUNTERS_GROUP.registerEvent("details.branch.checked.out")
  private val DETAILS_COMMIT_CHOSEN_EVENT = COUNTERS_GROUP.registerEvent("details.commit.chosen")
  private val DETAILS_NEXT_COMMIT_EVENT = COUNTERS_GROUP.registerEvent("details.next.commit.chosen")
  private val DETAILS_PREV_COMMIT_EVENT = COUNTERS_GROUP.registerEvent("details.prev.commit.chosen")
  private val DETAILS_CHANGE_EVENT = COUNTERS_GROUP.registerEvent("details.change.selected")
  private val DETAILS_CHECKS_EVENT = COUNTERS_GROUP.registerEvent("details.checks.opened")


  private val DETAILS_ACTION_EVENT = COUNTERS_GROUP.registerEvent("details.additional.actions.invoked",
                                                                  EventFields.Enum<GHPRAction>("action"),
                                                                  EventFields.Boolean("is_default"))

  fun logSelectorsOpened(project: Project) {
    SELECTORS_OPENED_EVENT.log(project)
  }

  fun logListOpened(project: Project) {
    LIST_OPENED_EVENT.log(project)
  }

  fun logListFiltersApplied(project: Project, filters: GHPRListSearchValue): Unit =
    FILTERS_APPLIED_EVENT.log(
      project,
      EventPair(FILTER_SEARCH_PRESENT, filters.searchQuery != null),
      EventPair(FILTER_STATE_PRESENT, filters.state != null),
      EventPair(FILTER_AUTHOR_PRESENT, filters.author != null),
      EventPair(FILTER_ASSIGNEE_PRESENT, filters.assignee != null),
      EventPair(FILTER_REVIEW_PRESENT, filters.reviewState != null),
      EventPair(FILTER_LABEL_PRESENT, filters.label != null),
      EventPair(FILTER_SORT_PRESENT, filters.sort != null)
    )

  fun logDetailsOpened(project: Project) {
    DETAILS_OPENED_EVENT.log(project)
  }

  fun logNewPRViewOpened(project: Project) {
    NEW_OPENED_EVENT.log(project)
  }

  fun logTimelineOpened(project: Project) {
    val count = FileEditorManager.getInstance(project).openFiles.count { it is GHPRTimelineVirtualFile }
    TIMELINE_OPENED_EVENT.log(project, count)
  }

  fun logDiffOpened(project: Project) {
    val count = FileEditorManager.getInstance(project).openFiles.count {
      it is GHPRDiffVirtualFile
    }
    DIFF_OPENED_EVENT.log(project, count)
  }

  fun logDetailsBranchesOpened(project: Project) {
    DETAILS_BRANCHES_EVENT.log(project)
  }

  fun logDetailsBranchCheckedOut(project: Project) {
    DETAILS_BRANCH_CHECKED_OUT_EVENT.log(project)
  }

  fun logDetailsCommitChosen(project: Project) {
    DETAILS_COMMIT_CHOSEN_EVENT.log(project)
  }

  fun logDetailsNextCommitChosen(project: Project) {
    DETAILS_NEXT_COMMIT_EVENT.log(project)
  }

  fun logDetailsPrevCommitChosen(project: Project) {
    DETAILS_PREV_COMMIT_EVENT.log(project)
  }

  fun logDetailsChecksOpened(project: Project) {
    DETAILS_CHECKS_EVENT.log(project)
  }

  fun logDetailsActionInvoked(project: Project, action: GHPRAction, isDefault: Boolean) {
    DETAILS_ACTION_EVENT.log(project, action, isDefault)
  }

  fun logChangeSelected(project: Project) {
    DETAILS_CHANGE_EVENT.log(project)
  }

  fun logMergedEvent(project: Project, method: GithubPullRequestMergeMethod) {
    MERGED_EVENT.log(project, method)
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

@Service(Service.Level.PROJECT)
private class GHServerVersionsCollector(
  private val project: Project,
  parentCs: CoroutineScope
) {

  private val scope = parentCs.childScope()

  init {
    val accountsFlow = service<GHAccountManager>().accountsState
    scope.launch {
      accountsFlow.collect {
        for (account in it) {
          val server = account.server
          if (server.isGithubDotCom) continue

          //TODO: load with auth to avoid rate-limit
          try {
            val metadata = service<GHEnterpriseServerMetadataLoader>().loadMetadata(server)
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
}
