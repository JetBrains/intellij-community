// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.filter

import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Session
import com.intellij.cce.filter.EvaluationFilter

data class SessionsFilter(override val name: String, val filters: Map<String, EvaluationFilter>) : NamedFilter {
  companion object {
    val ACCEPT_ALL = SessionsFilter("ALL", emptyMap())
  }

  //TODO: use actual tokens in these filters too
  fun apply(sessions: List<Session>): List<Session> {
    return sessions.filter { session -> filters.all { it.value.shouldEvaluate(CodeToken("", -1, session.properties) ) } }
  }
}