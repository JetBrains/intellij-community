// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

object AgentPromptContextMetadataKeys {
  const val SOURCE: String = "ctx.source"
  const val PHASE: String = "ctx.phase"
  const val LANGUAGE: String = "ctx.language"
  const val ORIGINAL_CHARS: String = "ctx.originalChars"
  const val INCLUDED_CHARS: String = "ctx.includedChars"
  const val TRUNCATED: String = "ctx.truncated"
  const val TRUNCATION_REASON: String = "ctx.truncationReason"
}

object AgentPromptContextTruncationReasons {
  const val NONE: String = "none"
  const val SOURCE_LIMIT: String = "source_limit"
  const val SOFT_CAP_PARTIAL: String = "soft_cap_partial"
  const val SOFT_CAP_OMITTED: String = "soft_cap_omitted"
}
