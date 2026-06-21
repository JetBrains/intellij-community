// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.rollout

internal const val ROLLOUT_FILE_PREFIX = "rollout-"
internal const val ROLLOUT_FILE_SUFFIX = ".jsonl"

internal fun isRolloutFileName(fileName: String): Boolean {
  return fileName.startsWith(ROLLOUT_FILE_PREFIX) && fileName.endsWith(ROLLOUT_FILE_SUFFIX)
}
