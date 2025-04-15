// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.GithubApiRequestOperation
import org.jetbrains.plugins.github.api.GithubServerPath
import org.jetbrains.plugins.github.api.data.GHEnterpriseServerMeta
import org.jetbrains.plugins.github.api.data.GithubPullRequestMergeMethod
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager
import org.jetbrains.plugins.github.pullrequest.ui.filters.GHPRListSearchValue
import org.jetbrains.plugins.github.util.GHEnterpriseServerMetadataLoader
import java.util.*

// TODO: Fix or replace a whole bunch of these statistics as they're no longer being collected since generalizing to Collab Tools
internal object GHPRStatisticsCollector : CounterUsagesCollector() {
  private val COUNTERS_GROUP = EventLogGroup("vcs.github.pullrequest.counters", 9)

  private val LOG = logger<GHPRStatisticsCollector>()

  override fun getGroup() = COUNTERS_GROUP

  //region: Pull Request list
  private val SELECTORS_OPENED_EVENT = COUNTERS_GROUP.registerEvent("selectors.opened")
  private val LIST_OPENED_EVENT = COUNTERS_GROUP.registerEvent("list.opened")

  fun logSelectorsOpened(project: Project) {
    SELECTORS_OPENED_EVENT.log(project)
  }

  fun logListOpened(project: Project) {
    LIST_OPENED_EVENT.log(project)
  }
  //endregion

  //region: Filters
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

  fun logListFiltersApplied(project: Project, filters: GHPRListSearchValue): Unit =
    FILTERS_APPLIED_EVENT.log(
      project,
      EventPair(FILTER_SEARCH_PRESENT, filters.searchQuery != null),
      EventPair(FILTER_STATE_PRESENT, filters.state != null),
      EventPair(FILTER_AUTHOR_PRESENT, filters.author != null),
      EventPair(FILTER_ASSIGNEE_PRESENT, filters.assignee != null),
      EventPair(FILTER_REVIEW_PRESENT, filters.reviewState != null),
      EventPair(FILTER_LABEL_PRESENT, filters.label != null),
      EventPair(FILTER_SORT_PRESENT, filters.label != null)
    )
  //endregion

  //region: Details / Create Tab
  private val DETAILS_OPENED_EVENT = COUNTERS_GROUP.registerEvent("details.opened")
  private val NEW_OPENED_EVENT = COUNTERS_GROUP.registerEvent("new.pr.view.opened")

  fun logDetailsOpened(project: Project) {
    DETAILS_OPENED_EVENT.log(project)
  }

  fun logNewPRViewOpened(project: Project) {
    NEW_OPENED_EVENT.log(project)
  }
  //endregion

  //region: Timeline
  private val TIMELINE_OPENED_EVENT = COUNTERS_GROUP.registerEvent("timeline.opened", EventFields.Int("count"))
  private val DIFF_OPENED_EVENT = COUNTERS_GROUP.registerEvent("diff.opened", EventFields.Int("count"))
  private val MERGED_EVENT = COUNTERS_GROUP.registerEvent("merged", EventFields.Enum<GithubPullRequestMergeMethod>("method") {
    it.name.uppercase(Locale.getDefault())
  })

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

  fun logChangeSelected(project: Project) {
    DETAILS_CHANGE_EVENT.log(project)
  }

  fun logMergedEvent(project: Project, method: GithubPullRequestMergeMethod) {
    MERGED_EVENT.log(project, method)
  }
  //endregion

  //region: Server Metadata
  private val anonymizedId = EventFields.AnonymizedId

  private val SERVER_META_EVENT = COUNTERS_GROUP.registerEvent("server.meta.collected", anonymizedId, EventFields.Version)

  fun logEnterpriseServerMeta(project: Project, server: GithubServerPath, meta: GHEnterpriseServerMeta) {
    SERVER_META_EVENT.log(project, server.toUrl(), meta.installedVersion)
  }
  //endregion

  //region: API
  private enum class RateLimitResource {
    Core, Search, CodeSearch, GraphQL,
    IntegrationManifest, DependencySnapshots, CodeScanningUpload, ActionsRunnerRegistration,
    SourceImport,
    Collaborators,
    Unknown;

    companion object {
      fun fromString(name: String): RateLimitResource = when (name) {
        "core", "rate" -> Core
        "search" -> Search
        "code_search" -> CodeSearch
        "graphql" -> GraphQL
        "integration_manifest" -> IntegrationManifest
        "dependency_snapshots" -> DependencySnapshots
        "code_scanning_upload" -> CodeScanningUpload
        "actions_runner_registration" -> ActionsRunnerRegistration
        "collaborators" -> Collaborators
        "source_import" -> SourceImport
        else -> Unknown
      }
    }
  }

  private val API_REQUEST_OPERATION_FIELD = EventFields.Enum<GithubApiRequestOperation>(
    "operation", description = "The type of operation executed."
  )

  private val API_REQUEST_RATELIMIT_REMAINING_FIELD = EventFields.Int(
    "rates_remaining",
    description = "The rate limit remaining for the resource as returned by GitHub." +
                  "This is not a good measure of rates-used-per-request, instead it's a measure of total usage for a session/user."
  )

  private val API_REQUEST_RATELIMIT_USED_FIELD = EventFields.Int(
    "rates_used",
    description = "The rate limit used during the request. " +
                  "In the case of GraphQL rates, this is taken directly from the request. " +
                  "In the case of REST rates, this defaults to 1, but may be changed in the future if GitHub changes their rate limit policy."
  )

  private val API_REQUEST_RATELIMIT_GUESSED_FIELD = EventFields.Boolean(
    "rates_used_guessed",
    description = "Whether the rates used during the request are guessed. " +
                  "If `false`, the rates are pulled directly from the request."
  )

  private val API_REQUEST_RATELIMIT_RESOURCE_FIELD = EventFields.Enum<RateLimitResource>(
    "rates_resource", description = "The resource from which rates are taken as returned by GitHub."
  )

  private val API_REQUEST_STATUS_CODE_FIELD = EventFields.Int(
    "status", description = "The status code of the response (200 = OK, 404 = Not Found, etc.)."
  )

  private val API_REQUEST_EVENT = COUNTERS_GROUP.registerIdeActivity(
    "api.request",
    startEventAdditionalFields = arrayOf(API_REQUEST_OPERATION_FIELD),
    finishEventAdditionalFields = arrayOf(API_REQUEST_RATELIMIT_REMAINING_FIELD, API_REQUEST_RATELIMIT_RESOURCE_FIELD, API_REQUEST_STATUS_CODE_FIELD)
  )

  private val API_REQUEST_RATES_EVENT = COUNTERS_GROUP.registerEvent(
    "api.rates",
    API_REQUEST_OPERATION_FIELD,
    API_REQUEST_RATELIMIT_USED_FIELD,
    API_REQUEST_RATELIMIT_GUESSED_FIELD,
    description = "Event that happens internally after we have tried to determine the rates used for a request."
  )

  fun logApiRequestStart(operation: GithubApiRequestOperation): StructuredIdeActivity =
    API_REQUEST_EVENT.started(null) {
      listOf(API_REQUEST_OPERATION_FIELD.with(operation))
    }

  fun logApiResponseReceived(
    activity: StructuredIdeActivity,
    remaining: Int, resourceName: String,
    statusCode: Int,
  ) {
    val resource = RateLimitResource.fromString(resourceName)

    if (resource == RateLimitResource.Unknown) {
      LOG.info("Unknown rate limit resource: ${resourceName}")
    }

    activity.finished {
      listOf(
        API_REQUEST_RATELIMIT_REMAINING_FIELD.with(remaining),
        API_REQUEST_RATELIMIT_RESOURCE_FIELD.with(resource),
        API_REQUEST_STATUS_CODE_FIELD.with(statusCode)
      )
    }
  }

  fun logApiResponseRates(operation: GithubApiRequestOperation, used: Int, isGuessed: Boolean) {
    LOG.debug("Rates { operation: $operation, used: $used, isGuessed: $isGuessed }")

    API_REQUEST_RATES_EVENT.log(operation, used, isGuessed)
  }
  //endregion
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
  parentCs: CoroutineScope,
) {

  private val scope = parentCs.childScope(javaClass.name)

  init {
    val accountsFlow = service<GHAccountManager>().accountsState
    scope.launch {
      accountsFlow.collect {
        for (account in it) {
          val server = account.server
          if (server.isGithubDotCom || server.isGheDataResidency) continue

          //TODO: load with auth to avoid rate-limit
          try {
            val metadata = service<GHEnterpriseServerMetadataLoader>().loadMetadata(server)
            GHPRStatisticsCollector.logEnterpriseServerMeta(project, server, metadata)
          }
          catch (_: Exception) {
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
