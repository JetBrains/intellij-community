// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.util

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId2
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.service
import org.jetbrains.plugins.gitlab.api.GitLabGQLQuery
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountManager

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
  private val COUNTERS_GROUP = EventLogGroup("vcs.gitlab.counters", 1)

  /**
   * Server returned 5** error
   */
  private val SERVER_ERROR_EVENT = COUNTERS_GROUP.registerEvent("api.error.server",
                                                                EventFields.Enum("requestName", GitLabApiRequestName::class.java),
                                                                EventFields.Boolean("isDefaultServer"),
                                                                EventFields.Version)

  fun logServerError(request: GitLabApiRequestName, isDefaultServer: Boolean, serverVersion: String?): Unit =
    SERVER_ERROR_EVENT.log(request, isDefaultServer, serverVersion)

  /**
   * Server returned error about missing GQL fields
   */
  private val GQL_MODEL_ERROR_EVENT =
    COUNTERS_GROUP.registerEvent("api.error.gql.model",
                                 EventFields.Enum("query", GitLabGQLQuery::class.java),
                                 EventFields.Version)

  fun logGqlModelError(query: GitLabGQLQuery, serverVersion: String?): Unit =
    GQL_MODEL_ERROR_EVENT.log(query, serverVersion)

  /**
   * Error occurred during response parsing
   */
  private val JSON_DESERIALIZATION_ERROR_EVENT =
    COUNTERS_GROUP.registerEvent("api.error.json.deserialization",
                                 EventFields.Class("class"),
                                 EventFields.Version)

  fun logJsonDeserializationError(clazz: Class<*>, serverVersion: String?): Unit =
    JSON_DESERIALIZATION_ERROR_EVENT.log(clazz, serverVersion)

  internal class GitLabCountersCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = COUNTERS_GROUP
  }
  //endregion
}

enum class GitLabApiRequestName {
  REST_GET_CURRENT_USER,
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
  REST_GET_MERGE_REQUEST_STATE_EVENTS,
  REST_GET_MERGE_REQUEST_LABEL_EVENTS,
  REST_GET_MERGE_REQUEST_MILESTONE_EVENTS,

  GQL_GET_CURRENT_USER,
  GQL_GET_MERGE_REQUEST,
  GQL_GET_MERGE_REQUEST_DISCUSSIONS,
  GQL_GET_PROJECT_LABELS,
  GQL_GET_PROJECT_MEMBERS,
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
      GitLabGQLQuery.GET_PROJECT_MEMBERS -> GQL_GET_PROJECT_MEMBERS
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

