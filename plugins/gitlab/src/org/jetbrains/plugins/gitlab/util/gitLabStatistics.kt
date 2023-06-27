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
import org.jetbrains.plugins.gitlab.api.GitLabGQLQuery
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager
import org.jetbrains.plugins.gitlab.mergerequest.ui.filters.GitLabMergeRequestsFiltersValue

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
  private val COUNTERS_GROUP = EventLogGroup("vcs.gitlab.counters",  version = 4)

  /**
   * Server returned 5** error
   */
  private val SERVER_ERROR_EVENT = COUNTERS_GROUP.registerEvent("api.server.error.occurred",
                                                                EventFields.Enum("requestName", GitLabApiRequestName::class.java),
                                                                EventFields.Boolean("isDefaultServer"),
                                                                EventFields.Version)

  fun logServerError(request: GitLabApiRequestName, isDefaultServer: Boolean, serverVersion: String?): Unit =
    SERVER_ERROR_EVENT.log(request, isDefaultServer, serverVersion)

  /**
   * Server returned error about missing GQL fields
   */
  private val GQL_MODEL_ERROR_EVENT =
    COUNTERS_GROUP.registerEvent("api.gql.model.error.occurred",
                                 EventFields.Enum("query", GitLabGQLQuery::class.java),
                                 EventFields.Version)

  fun logGqlModelError(query: GitLabGQLQuery, serverVersion: String?): Unit =
    GQL_MODEL_ERROR_EVENT.log(query, serverVersion)

  /**
   * Error occurred during response parsing
   */
  private val JSON_DESERIALIZATION_ERROR_EVENT =
    COUNTERS_GROUP.registerEvent("api.json.deserialization.error.occurred",
                                 EventFields.Class("class"),
                                 EventFields.Version)

  fun logJsonDeserializationError(clazz: Class<*>, serverVersion: String?): Unit =
    JSON_DESERIALIZATION_ERROR_EVENT.log(clazz, serverVersion)

  internal class GitLabCountersCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = COUNTERS_GROUP
  }

  private val FILTER_SEARCH_PRESENT = EventFields.Boolean("hasSearch")
  private val FILTER_STATE_PRESENT = EventFields.Boolean("hasState")
  private val FILTER_AUTHOR_PRESENT = EventFields.Boolean("hasAuthor")
  private val FILTER_ASSIGNEE_PRESENT = EventFields.Boolean("hasAssignee")
  private val FILTER_REVIEWER_PRESENT = EventFields.Boolean("hasReviewer")
  private val FILTER_LABEL_PRESENT = EventFields.Boolean("hasLabel")

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

  fun logMrFiltersApplied(filters: GitLabMergeRequestsFiltersValue): Unit =
    FILTERS_APPLIED_EVENT.log(
      EventPair(FILTER_SEARCH_PRESENT, filters.searchQuery != null),
      EventPair(FILTER_STATE_PRESENT, filters.state != null),
      EventPair(FILTER_AUTHOR_PRESENT, filters.author != null),
      EventPair(FILTER_ASSIGNEE_PRESENT, filters.assignee != null),
      EventPair(FILTER_REVIEWER_PRESENT, filters.reviewer != null),
      EventPair(FILTER_LABEL_PRESENT, filters.label != null)
    )


  /**
   * Merge requests toolwindow login view was opened
   */
  private val MR_TW_LOGIN_OPENED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.toolwindow.login.opened")

  fun logMrTwLoginOpened(): Unit = MR_TW_LOGIN_OPENED_EVENT.log()

  /**
   * Merge requests toolwindow was opened
   */
  private val MR_LIST_OPENED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.list.opened")

  fun logMrListOpened(): Unit = MR_LIST_OPENED_EVENT.log()

  /**
   * Merge request details were opened
   */
  private val MR_DETAILS_OPENED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.details.opened")

  fun logMrDetailsOpened(): Unit = MR_DETAILS_OPENED_EVENT.log()

  /**
   * Merge request diff was opened
   */
  private val MR_DIFF_OPENED_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.diff.opened", EventFields.Boolean("isCumulative"))

  fun logMrDiffOpened(isCumulative: Boolean): Unit = MR_DIFF_OPENED_EVENT.log(isCumulative)

  /**
   * Merge request action was executed
   */
  private val MR_ACTION_FIELD = EventFields.Enum("action", MergeRequestAction::class.java)

  enum class MergeRequestAction {
    MERGE,
    SQUASH_MERGE,
    REBASE,
    APPROVE,
    UNAPPROVE,
    CLOSE,
    REOPEN,
    SET_REVIEWERS,
    ADD_NOTE,
    ADD_DIFF_NOTE,
    ADD_DISCUSSION_NOTE,
    CHANGE_DISCUSSION_RESOLVE,
    UPDATE_NOTE,
    DELETE_NOTE,
    SUBMIT_DRAFT_NOTES,
    POST_REVIEW
  }

  /**
   * Some mutation action was requested on merge request via API
   */
  private val MR_ACTION_EVENT = COUNTERS_GROUP.registerEvent("mergerequests.action.performed", MR_ACTION_FIELD)

  fun logMrActionExecuted(action: MergeRequestAction): Unit = MR_ACTION_EVENT.log(action)
  //endregion
}

enum class GitLabApiRequestName {
  REST_GET_CURRENT_USER,
  REST_GET_PROJECT_USERS,
  REST_GET_COMMIT,
  REST_GET_COMMIT_DIFF,
  REST_GET_MERGE_REQUEST_DIFF,
  REST_DELETE_DRAFT_NOTE,
  REST_GET_DRAFT_NOTES,
  REST_SUBMIT_DRAFT_NOTES,
  REST_UPDATE_DRAFT_NOTE,
  REST_GET_MERGE_REQUESTS,
  REST_APPROVE_MERGE_REQUEST,
  REST_UNAPPROVE_MERGE_REQUEST,
  REST_REBASE_MERGE_REQUEST,
  REST_GET_MERGE_REQUEST_STATE_EVENTS,
  REST_GET_MERGE_REQUEST_LABEL_EVENTS,
  REST_GET_MERGE_REQUEST_MILESTONE_EVENTS,

  GQL_GET_CURRENT_USER,
  GQL_GET_MERGE_REQUEST,
  GQL_GET_MERGE_REQUEST_DISCUSSIONS,
  GQL_GET_PROJECT_LABELS,
  GQL_TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE,
  GQL_CREATE_NOTE,
  GQL_CREATE_DIFF_NOTE,
  GQL_CREATE_REPLY_NOTE,
  GQL_UPDATE_NOTE,
  GQL_DESTROY_NOTE,
  GQL_MERGE_REQUEST_ACCEPT,
  GQL_MERGE_REQUEST_SET_DRAFT,
  GQL_MERGE_REQUEST_SET_REVIEWERS,
  GQL_MERGE_REQUEST_UPDATE;

  companion object {
    fun of(gqlQuery: GitLabGQLQuery): GitLabApiRequestName = when (gqlQuery) {
      GitLabGQLQuery.GET_CURRENT_USER -> GQL_GET_CURRENT_USER
      GitLabGQLQuery.GET_MERGE_REQUEST -> GQL_GET_MERGE_REQUEST
      GitLabGQLQuery.GET_MERGE_REQUEST_DISCUSSIONS -> GQL_GET_MERGE_REQUEST_DISCUSSIONS
      GitLabGQLQuery.GET_PROJECT_LABELS -> GQL_GET_PROJECT_LABELS
      GitLabGQLQuery.TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE -> GQL_TOGGLE_MERGE_REQUEST_DISCUSSION_RESOLVE
      GitLabGQLQuery.CREATE_NOTE -> GQL_CREATE_NOTE
      GitLabGQLQuery.CREATE_DIFF_NOTE -> GQL_CREATE_DIFF_NOTE
      GitLabGQLQuery.CREATE_REPLY_NOTE -> GQL_CREATE_REPLY_NOTE
      GitLabGQLQuery.UPDATE_NOTE -> GQL_UPDATE_NOTE
      GitLabGQLQuery.DESTROY_NOTE -> GQL_DESTROY_NOTE
      GitLabGQLQuery.MERGE_REQUEST_ACCEPT -> GQL_MERGE_REQUEST_ACCEPT
      GitLabGQLQuery.MERGE_REQUEST_SET_DRAFT -> GQL_MERGE_REQUEST_SET_DRAFT
      GitLabGQLQuery.MERGE_REQUEST_SET_REVIEWERS -> GQL_MERGE_REQUEST_SET_REVIEWERS
      GitLabGQLQuery.MERGE_REQUEST_UPDATE -> GQL_MERGE_REQUEST_UPDATE
    }
  }
}

