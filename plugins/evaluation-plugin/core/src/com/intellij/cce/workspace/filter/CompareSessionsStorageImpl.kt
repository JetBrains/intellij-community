// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Session

class CompareSessionsStorageImpl(val filter: CompareSessionsFilter) : CompareSessionsStorage {
  private val type2sessions = mutableMapOf<String, MutableList<Session>>()
  private var processed = false

  override fun add(type: String, sessions: List<Session>) {
    assert(!type2sessions.containsKey(type)) { "Type $type is already in storage" }
    type2sessions[type] = sessions.toMutableList()
    processed = false
  }

  override val reportName: String = filter.name

  override fun clear() = type2sessions.clear()

  override fun get(type: String): List<Session> {
    if (processed) return type2sessions[type] ?: emptyList()
    val baseSessions = type2sessions[filter.evaluationType] ?: return emptyList()
    val newBaseSessions = mutableListOf<Session>()
    for (session in baseSessions) {
      val newSession = Session(session)
      for ((i, lookup) in session.lookups.withIndex()) {
        var deletionCount = 0
        for (currentType in type2sessions.keys.filter { it != filter.evaluationType }) {
          val currentSession = findSession(currentType, session.offset) ?: continue
          if (!filter.check(lookup, currentSession.lookups[i], session.expectedText)) {
            currentSession.removeLookup(currentSession.lookups[i])
            if (currentSession.lookups.isEmpty()) type2sessions.getValue(currentType).remove(currentSession)
            deletionCount++
            continue
          }
        }
        if (deletionCount != type2sessions.keys.size - 1) newSession.addLookup(lookup)
      }
      if (newSession.lookups.isNotEmpty()) newBaseSessions.add(newSession)
    }
    type2sessions[filter.evaluationType] = newBaseSessions
    processed = true
    return type2sessions[type] ?: emptyList()
  }

  private fun findSession(type: String, offset: Int): Session? = type2sessions.getValue(type).find { it.offset == offset }
}