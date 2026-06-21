// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

internal fun Throwable.isCodexThreadNotLoadedError(): Boolean {
  return generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .any { message -> message.trim().startsWith("thread not loaded:") }
}
