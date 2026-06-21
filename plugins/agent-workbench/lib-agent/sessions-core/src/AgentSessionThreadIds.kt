// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core

private const val PENDING_THREAD_ID_PREFIX = "new-"

fun isAgentSessionPendingThreadId(threadId: String): Boolean {
  return threadId.trim().startsWith(PENDING_THREAD_ID_PREFIX)
}

fun normalizeConcreteAgentSessionThreadId(threadId: String): String? {
  val normalizedThreadId = threadId.trim()
  return normalizedThreadId.takeIf { it.isNotEmpty() && !isAgentSessionPendingThreadId(it) }
}
