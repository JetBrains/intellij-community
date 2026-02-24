// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

private const val MAX_TRACKED_ORPHAN_SUB_AGENT_IDS = 4_096

@Service(Service.Level.APP)
@State(name = "CodexSubAgentArchiveState", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class CodexSubAgentArchiveStateService : PersistentStateComponent<CodexSubAgentArchiveStateService.CodexSubAgentArchiveState> {

  private var myState = CodexSubAgentArchiveState()

  override fun getState(): CodexSubAgentArchiveState {
    return myState
  }

  override fun loadState(state: CodexSubAgentArchiveState) {
    this.myState = state
  }

  @Synchronized
  fun markArchiveAttempted(threadId: String): Boolean {
    val normalizedThreadId = threadId.trim()
    if (normalizedThreadId.isEmpty()) {
      return false
    }
    if (normalizedThreadId in myState.attemptedThreadIds) {
      return false
    }

    val updated = LinkedHashSet(myState.attemptedThreadIds)
    updated.add(normalizedThreadId)
    while (updated.size > MAX_TRACKED_ORPHAN_SUB_AGENT_IDS) {
      val oldest = updated.firstOrNull() ?: break
      updated.remove(oldest)
    }
    myState = CodexSubAgentArchiveState(
      attemptedThreadIds = ArrayList(updated),
    )
    return true
  }

  class CodexSubAgentArchiveState {
    @JvmField
    var attemptedThreadIds: MutableList<String> = ArrayList()

    constructor()

    constructor(attemptedThreadIds: MutableList<String>) {
      this.attemptedThreadIds = attemptedThreadIds
    }
  }
}
