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