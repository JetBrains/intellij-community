package com.intellij.cce.workspace.filter

import com.intellij.cce.core.Session

class SessionLookupsFilter(val filters: List<LookupFilter>) {
  fun filter(sessions: List<Session>) {
    if (filters.isEmpty()) return

    for (session in sessions) {
      val lastLookupIndex = session.lookups.size - 1
      for (i in lastLookupIndex downTo 0) {
        val lookup = session.lookups[i]
        if (filters.any { it.shouldRemove(lookup) }) {
          session.removeLookup(lookup)
        }
      }
    }
  }
}