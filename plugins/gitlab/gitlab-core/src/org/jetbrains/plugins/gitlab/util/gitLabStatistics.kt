// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gitlab.api.GitLabEdition
import org.jetbrains.plugins.gitlab.api.GitLabGQLQuery
import org.jetbrains.plugins.gitlab.api.GitLabServerMetadata
import org.jetbrains.plugins.gitlab.api.GitLabVersion
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue
import org.jetbrains.plugins.gitlab.util.GitLabApiRequestName.*

@PublishedApi
internal object GitLabStatistics {
  //region State
  private val STATE_GROUP = EventLogGroup("vcs.gitlab", 1)

  private val ACCOUNTS_EVENT: EventId2<Int, Boolean> =
    STATE_GROUP.registerEvent("accounts", EventFields.Count, EventFields.Boolean("has_enterprise"))

  internal class GitLabAccountsStatisticsCollector : ApplicationUsagesCollector() {
    override fun getGroup(): EventLogGroup = STATE_GROUP

    override fun getMetrics(): Set<MetricEvent> {
      val accountManager = service<GitLabAccountManager>()
      val hasAccountsWithNonDefaultHost = accountManager.accountsState.value.any { !it.server.isDefault }

      return setOf(ACCOUNTS_EVENT.metric(accountManager.accountsState.value.size, hasAccountsWithNonDefaultHost))
    }
  }
  //endregion

  //region Counters
   private val COUNTERS_GROUP = EventLogGroup("vcs.gitlab.counters", version = 23)

  /**
   * Server metadata was fetched
   */
  private val SERVER_METADATA_FETCHED_EVENT = COUNTERS_GROUP.registerEvent("api.server.version-fetched",
                                                                           EventFields.Enum("edition", GitLabEdition::class.java),
                                                                           EventFields.Version)

  /**
   * Logs a metadata fetched event.
   */
  fun logServerMetadataFetched(metadata: GitLabServerMetadata): Unit =
    SERVER_METADATA_FETCHED_EVENT.log(metadata.edition, metadata.version.toString())

  /**
   * Server returned 5** error
   */
  private val SERVER_ERROR_EVENT = COUNTERS_GROUP.registerEvent("api.server.error.occurred",
                                                                EventFields.Enum("request_name", GitLabApiRequestName::class.java),
                                                                EventFields.Boolean("is_default_server"),
                                                                EventFields.Version)

  fun logServerError(request: GitLabApiRequestName, isDefaultServer: Boolean, serverVersion: GitLabVersion?): Unit =
    SERVER_ERROR_EVENT.log(request, isDefaultServer, serverVersion?.toString())

  /**
   * Server returned error about missing GQL fields
   */
  private val GQL_MODEL_ERROR_EVENT =
    COUNTERS_GROUP.registerEvent("api.gql.model.error.occurred",
                                 EventFields.Enum("query", GitLabGQLQuery::class.java),
                                 EventFields.Version)

  fun logGqlModelError(query: GitLabGQLQuery, serverVersion: GitLabVersion?): Unit =
    GQL_MODEL_ERROR_EVENT.log(query, serverVersion?.toString())

  /**
   * Error occurred during response parsing
   */
  private val JSON_DESERIALIZATION_ERROR_EVENT =
    COUNTERS_GROUP.registerEvent("api.json.deserialization.error.occurred",
                                 EventFields.Class("class"),
                                 EventFields.Version)

  fun logJsonDeserializationError(clazz: Class<*>, serverVersion: GitLabVersion?): Unit =
    JSON_DESERIALIZATION_ERROR_EVENT.log(clazz, serverVersion?.toString())

  internal class GitLabCountersCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = COUNTERS_GROUP
  }

  private val FILTER_SEARCH_PRESENT = EventFields.Boolean("has_search")
  private val FILTER_STATE_PRESENT = EventFields.Boolean("has_state")
  private val FILTER_AUTHOR_PRESENT = EventFields.Boolean("has_author")
  private val FILTER_ASSIGNEE_PRESENT = EventFields.Boolean("has_assignee")
  private val FILTER_REVIEWER_PRESENT = EventFields.Boolean("has_reviewer")
  private val FILTER_LABEL_PRESENT = EventFields.Boolean("has_label")

  /**
   * Merge requests list filters applied
   */
  private val FILTERS_APPLIED_EVENT = COUNTERS_GROUP.registerVarargEvent("mergerequests.list.filters.applied",
                                                                         FILTER_SEARCH_PRESENT,
                                                                         FILTER_STATE_PRESENT,
                                                                         FILTER_AUTHOR_PRESENT,
                                                                         FILTER_ASSIGNEE_PRESENT,
                                                                         FILTER_REVIEWER_PRESENT,
                                                                         FILTER_LABEL_PRESENT)

  fun logMrFiltersApplied(project: Project?, filters: GitLabMergeRequestsFiltersValue): Unit =
    FILTERS_APPLIED_EVENT.log(
      project,
      EventPair(FILTER_SEARCH_PRESENT, filters.searchQuery != null),
      EventPair(FILTER_STATE_PRESENT, filters.state != null),
      EventPair(FILTER_AUTHOR_PRESENT, filters.author != null),
      EventPair(FILTER_ASSIGNEE_PRESENT, filters.assignee != null),
      EventPair(FILTER_REVIEWER_PRESENT, filters.reviewer != null),
      EventPair(FILTER_LABEL_PRESENT, filters.label != null)
    )

  /**
   * Merge request diff was opened
   */
  private val MR_DIFF_OPENED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.diff.opened", EventFields.Boolean("is_cumulative"))

  fun logMrDiffOpened(project: Project, isCumulative: Boolean): Unit = MR_DIFF_OPENED_EVENT.log(project, isCumulative)

  /**
   * Merge request action was executed
   */
  private val MR_ACTION_FIELD = EventFields.Enum<MergeRequestAction>("action")

  /**
   * Where merge request notes action was executed
   */
  private val MR_NOTE_ACTION_PLACE_FIELD = EventFields.NullableEnum<MergeRequestNoteActionPlace>("note_action_place")

  enum class MergeRequestAction {
    MERGE,
    SQUASH_MERGE,
    REBASE,
    APPROVE,
    UNAPPROVE,
    CLOSE,
    REOPEN,
    SET_REVIEWERS,
    REVIEWER_REREVIEW,
    ADD_NOTE,
    ADD_DRAFT_NOTE,
    ADD_DIFF_NOTE,
    ADD_DRAFT_DIFF_NOTE,
    ADD_DISCUSSION_NOTE,
    ADD_DRAFT_DISCUSSION_NOTE,
    CHANGE_DISCUSSION_RESOLVE,
    UPDATE_NOTE,
    DELETE_NOTE,
    POST_DRAFT_NOTE,
    SUBMIT_DRAFT_NOTES,
    POST_REVIEW,
    BRANCH_CHECKOUT,
    SHOW_BRANCH_IN_LOG
  }

  enum class MergeRequestNoteActionPlace {
    TIMELINE,
    DIFF,
    EDITOR
  }

  /**
   * Some mutation action was requested on merge request via API
   */
  private val MR_ACTION_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.action.performed", MR_ACTION_FIELD, MR_NOTE_ACTION_PLACE_FIELD)

  fun logMrActionExecuted(project: Project, action: MergeRequestAction, place: MergeRequestNoteActionPlace? = null): Unit =
    MR_ACTION_EVENT.log(project, action, place)

  private val SNIPPET_ACTION_EVENT = COUNTERS_GROUP.registerEvent("snippets.action.performed",
                                                                  EventFields.Enum<SnippetAction>("action"))

  fun logSnippetActionExecuted(project: Project, action: SnippetAction): Unit = SNIPPET_ACTION_EVENT.log(project, action)

  enum class SnippetAction {
    CREATE_OPEN_DIALOG,
    CREATE_OK,
    CREATE_CANCEL,
    CREATE_CREATED,
    CREATE_ERRORED
  }

  /**
   * Merge request creation started
   */
  private val MR_CREATION_STARTED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.creation.started")

  fun logMrCreationStarted(project: Project): Unit = MR_CREATION_STARTED_EVENT.log(project)

  /**
   * Merge request creation succeeded
   */
  private val MR_CREATION_SUCCEEDED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.creation.succeeded")

  fun logMrCreationSucceeded(project: Project): Unit = MR_CREATION_SUCCEEDED_EVENT.log(project)

  /**
   * Merge request creation failed
   */
  private val MR_CREATION_FAILED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.creation.failed", EventFields.Int("error_status_code"))

  fun logMrCreationFailed(project: Project, errorStatusCode: Int): Unit = MR_CREATION_FAILED_EVENT.log(project, errorStatusCode)

  /**
   * Reviewers have been adjusted to the creation of merge request
   */
  private val MR_CREATION_REVIEWERS_ADJUSTED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.creation.reviewer.adjusted")

  fun logMrCreationReviewersAdjusted(project: Project): Unit = MR_CREATION_REVIEWERS_ADJUSTED_EVENT.log(project)

  /**
   * Merge request creation branches were changed
   */
  private val MR_CREATION_BRANCHES_CHANGED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.creation.branches.changed")

  fun logMrCreationBranchesChanged(project: Project): Unit = MR_CREATION_BRANCHES_CHANGED_EVENT.log(project)

  /**
   * GitLab tool window tab <type> was opened from <place>
   */
  private val TW_TAB_OPENED_EVENT = COUNTERS_GROUP.registerEvent(
    "toolwindow.tab.opened",
    EventFields.Enum<ToolWindowTabType>("tab_type"),
    EventFields.Enum<ToolWindowOpenTabActionPlace>("open_action_place")
  )

  fun logTwTabOpened(project: Project, tabType: ToolWindowTabType, actionPlace: ToolWindowOpenTabActionPlace): Unit =
    TW_TAB_OPENED_EVENT.log(project, tabType, actionPlace)

  /**
   * GitLab tool window tab <type> was closed
   */
  private val TW_TAB_CLOSED_EVENT = COUNTERS_GROUP.registerEvent("toolwindow.tab.closed", EventFields.Enum<ToolWindowTabType>("tab_type"))

  fun logTwTabClosed(project: Project, tabType: ToolWindowTabType): Unit = TW_TAB_CLOSED_EVENT.log(project, tabType)

  enum class ToolWindowTabType {
    CREATION,
    DETAILS,
    LIST,
    SELECTOR
  }

  enum class ToolWindowOpenTabActionPlace {
    ACTION,
    CREATION,
    TOOLWINDOW,
    NOTIFICATION,
    TIMELINE_LINK
  }
  //endregion
}

enum class GitLabApiRequestName {
  REST_GET_CURRENT_USER,
  REST_GET_PROJECT_NAMESPACE,
  REST_GET_PROJECT_USERS,
  REST_GET_COMMIT,
  REST_GET_COMMIT_DIFF,
  REST_GET_MERGE_REQUEST_DIFF,
  REST_GET_MERGE_REQUEST_CHANGES,
  REST_DELETE_DRAFT_NOTE,
  REST_GET_DRAFT_NOTES,
  REST_SUBMIT_DRAFT_NOTES,
  REST_SUBMIT_SINGLE_DRAFT_NOTE,
  REST_CREATE_DRAFT_NOTE,
  REST_UPDATE_DRAFT_NOTE,
  REST_GET_MERGE_REQUESTS,
  REST_APPROVE_MERGE_REQUEST,
  REST_UNAPPROVE_MERGE_REQUEST,
  REST_REBASE_MERGE_REQUEST,
  REST_PUT_MERGE_REQUEST_REVIEWERS,
  REST_GET_MERGE_REQUEST_COMMITS,
  REST_GET_MERGE_REQUEST_STATE_EVENTS,
  REST_GET_MERGE_REQUEST_LABEL_EVENTS,
  REST_GET_MERGE_REQUEST_MILESTONE_EVENTS,

  GQL_GET_METADATA,
  GQL_GET_CURRENT_USER,
  GQL_GET_MERGE_REQUEST,
  GQL_FIND_MERGE_REQUEST,
  GQL_GET_MERGE_REQUEST_COMMITS,
  GQL_GET_MERGE_REQUEST_DISCUSSIONS,
  GQL_GET_PROJECT,
  GQL_GET_PROJECT_LABELS,
  GQL_GET_PROJECT_REPOSITORY,
  GQL_GET_PROJECT_WORK_ITEMS,
  GQL_GET_MEMBER_PROJECTS_FOR_CLONE,
  GQL_GET_MEMBER_PROJECTS_FOR_SNIPPETS,
  GQL_TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE,
  GQL_AWARD_EMOJI_TOGGLE,
  GQL_CREATE_NOTE,
  GQL_CREATE_DIFF_NOTE,
  GQL_CREATE_REPLY_NOTE,
  GQL_CREATE_SNIPPET,
  GQL_UPDATE_NOTE,
  GQL_UPDATE_SNIPPET_BLOB,
  GQL_DESTROY_NOTE,
  GQL_MERGE_REQUEST_ACCEPT,
  GQL_MERGE_REQUEST_CREATE,
  GQL_MERGE_REQUEST_SET_DRAFT,
  GQL_MERGE_REQUEST_SET_REVIEWERS,
  GQL_MERGE_REQUEST_UPDATE,
  GQL_MERGE_REQUEST_REVIEWER_REREVIEW;

  companion object {
    fun of(gqlQuery: GitLabGQLQuery): GitLabApiRequestName = when (gqlQuery) {
      GitLabGQLQuery.GET_METADATA -> GQL_GET_METADATA
      GitLabGQLQuery.GET_CURRENT_USER -> GQL_GET_CURRENT_USER
      GitLabGQLQuery.GET_MERGE_REQUEST -> GQL_GET_MERGE_REQUEST
      GitLabGQLQuery.FIND_MERGE_REQUESTS -> GQL_FIND_MERGE_REQUEST
      GitLabGQLQuery.GET_MERGE_REQUEST_COMMITS -> GQL_GET_MERGE_REQUEST_COMMITS
      GitLabGQLQuery.GET_MERGE_REQUEST_DISCUSSIONS -> GQL_GET_MERGE_REQUEST_DISCUSSIONS
      GitLabGQLQuery.GET_PROJECT -> GQL_GET_PROJECT
      GitLabGQLQuery.GET_PROJECT_LABELS -> GQL_GET_PROJECT_LABELS
      GitLabGQLQuery.GET_PROJECT_WORK_ITEMS -> GQL_GET_PROJECT_WORK_ITEMS
      GitLabGQLQuery.GET_MEMBER_PROJECTS_FOR_CLONE -> GQL_GET_MEMBER_PROJECTS_FOR_CLONE
      GitLabGQLQuery.GET_MEMBER_PROJECTS_FOR_SNIPPETS -> GQL_GET_MEMBER_PROJECTS_FOR_SNIPPETS
      GitLabGQLQuery.TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE -> GQL_TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE
      GitLabGQLQuery.AWARD_EMOJI_TOGGLE -> GQL_AWARD_EMOJI_TOGGLE
      GitLabGQLQuery.CREATE_NOTE -> GQL_CREATE_NOTE
      GitLabGQLQuery.CREATE_DIFF_NOTE -> GQL_CREATE_DIFF_NOTE
      GitLabGQLQuery.CREATE_REPLY_NOTE -> GQL_CREATE_REPLY_NOTE
      GitLabGQLQuery.CREATE_SNIPPET -> GQL_CREATE_SNIPPET
      GitLabGQLQuery.UPDATE_NOTE -> GQL_UPDATE_NOTE
      GitLabGQLQuery.UPDATE_SNIPPET_BLOB -> GQL_UPDATE_SNIPPET_BLOB
      GitLabGQLQuery.DESTROY_NOTE -> GQL_DESTROY_NOTE
      GitLabGQLQuery.MERGE_REQUEST_ACCEPT -> GQL_MERGE_REQUEST_ACCEPT
      GitLabGQLQuery.MERGE_REQUEST_CREATE -> GQL_MERGE_REQUEST_CREATE
      GitLabGQLQuery.MERGE_REQUEST_SET_DRAFT -> GQL_MERGE_REQUEST_SET_DRAFT
      GitLabGQLQuery.MERGE_REQUEST_SET_REVIEWERS -> GQL_MERGE_REQUEST_SET_REVIEWERS
      GitLabGQLQuery.MERGE_REQUEST_UPDATE -> GQL_MERGE_REQUEST_UPDATE
      GitLabGQLQuery.MERGE_REQUEST_REVIEWER_REREVIEW -> GQL_MERGE_REQUEST_REVIEWER_REREVIEW
    }
  }
}

