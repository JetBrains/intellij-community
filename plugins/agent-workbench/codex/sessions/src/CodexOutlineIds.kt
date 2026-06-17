// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

private const val CODEX_USER_PROMPT_OUTLINE_ID_PREFIX = "codex-user-prompt:"

internal fun codexUserPromptOutlineItemId(userPromptIndex: Int): String {
  return "$CODEX_USER_PROMPT_OUTLINE_ID_PREFIX$userPromptIndex"
}

internal fun parseCodexUserPromptOutlineItemIndex(itemId: String): Int? {
  if (!itemId.startsWith(CODEX_USER_PROMPT_OUTLINE_ID_PREFIX)) {
    return null
  }
  val index = itemId.removePrefix(CODEX_USER_PROMPT_OUTLINE_ID_PREFIX).toIntOrNull() ?: return null
  return index.takeIf { it >= 0 }
}
