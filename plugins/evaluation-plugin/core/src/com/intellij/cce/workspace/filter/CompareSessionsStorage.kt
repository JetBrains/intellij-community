// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Session

interface CompareSessionsStorage {
  companion object {
    val ACCEPT_ALL = object : CompareSessionsStorage {
      private val type2sessions = mutableMapOf<String, List<Session>>()
      override val reportName: String = "WITHOUT COMPARISON"
      override fun add(type: String, sessions: List<Session>) = type2sessions.set(type, sessions)
      override fun get(type: String): List<Session> = type2sessions[type] ?: emptyList()
      override fun clear() = type2sessions.clear()
    }
  }

  val reportName: String
  fun add(type: String, sessions: List<Session>)
  fun clear()
  fun get(type: String): List<Session>
}