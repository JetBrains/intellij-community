package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Session
import com.intellij.cce.filter.EvaluationFilter

data class SessionsFilter(override val name: String, val filters: Map<String, EvaluationFilter>) : NamedFilter {
  companion object {
    val ACCEPT_ALL = SessionsFilter("ALL", emptyMap())
  }

  fun apply(sessions: List<Session>): List<Session> {
    return sessions.filter { session -> filters.all { it.value.shouldEvaluate(session.properties) } }
  }
}