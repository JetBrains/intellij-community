// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.prompt

import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.AgentSessionThread

data class AgentPromptContextItem(
  @JvmField val rendererId: String,
  @JvmField val title: String?,
  @JvmField val body: String,
  @JvmField val payload: AgentPromptPayloadValue = AgentPromptPayloadValue.Obj.EMPTY,
  @JvmField val source: String = "unknown",
  @JvmField val phase: AgentPromptContextContributorPhase? = null,
  @JvmField val truncation: AgentPromptContextTruncation = AgentPromptContextTruncation.none(body.length),
)

sealed interface AgentPromptPayloadValue {
  data class Obj(
    @JvmField val fields: Map<String, AgentPromptPayloadValue>,
  ) : AgentPromptPayloadValue {
    companion object {
      @JvmField
      val EMPTY: Obj = Obj(emptyMap())
    }
  }

  data class Arr(
    @JvmField val items: List<AgentPromptPayloadValue>,
  ) : AgentPromptPayloadValue

  data class Str(
    @JvmField val value: String,
  ) : AgentPromptPayloadValue

  data class Num(
    @JvmField val value: String,
  ) : AgentPromptPayloadValue

  data class Bool(
    @JvmField val value: Boolean,
  ) : AgentPromptPayloadValue

  data object Null : AgentPromptPayloadValue
}

enum class AgentPromptContextTruncationReason {
  NONE,
  SOURCE_LIMIT,
  SOFT_CAP_PARTIAL,
  SOFT_CAP_OMITTED,
}

data class AgentPromptContextTruncation(
  @JvmField val originalChars: Int,
  @JvmField val includedChars: Int,
  @JvmField val reason: AgentPromptContextTruncationReason,
) {
  companion object {
    fun none(chars: Int): AgentPromptContextTruncation {
      return AgentPromptContextTruncation(
        originalChars = chars.coerceAtLeast(0),
        includedChars = chars.coerceAtLeast(0),
        reason = AgentPromptContextTruncationReason.NONE,
      )
    }
  }
}

data class AgentPromptInitialMessageRequest(
  @JvmField val prompt: String,
  @JvmField val projectPath: String? = null,
  @JvmField val contextItems: List<AgentPromptContextItem> = emptyList(),
  @JvmField val contextEnvelopeSummary: AgentPromptContextEnvelopeSummary? = null,
)

data class AgentPromptContextEnvelopeSummary(
  @JvmField val softCapChars: Int = 12_000,
  @JvmField val softCapExceeded: Boolean = false,
  @JvmField val autoTrimApplied: Boolean = false,
)

data class AgentPromptLaunchRequest(
  val provider: AgentSessionProvider,
  @JvmField val projectPath: String,
  @JvmField val launchMode: AgentSessionLaunchMode,
  @JvmField val initialMessageRequest: AgentPromptInitialMessageRequest,
  @JvmField val targetThreadId: String? = null,
  @JvmField val preferredDedicatedFrame: Boolean? = null,
)

enum class AgentPromptLaunchError {
  PROVIDER_UNAVAILABLE,
  UNSUPPORTED_LAUNCH_MODE,
  TARGET_THREAD_NOT_FOUND,
  INTERNAL_ERROR,
}

data class AgentPromptLaunchResult(
  @JvmField val launched: Boolean,
  @JvmField val error: AgentPromptLaunchError? = null,
) {
  companion object {
    @JvmField
    val SUCCESS: AgentPromptLaunchResult = AgentPromptLaunchResult(launched = true)

    fun failure(error: AgentPromptLaunchError): AgentPromptLaunchResult {
      return AgentPromptLaunchResult(launched = false, error = error)
    }
  }
}

data class AgentPromptExistingThreadsSnapshot(
  @JvmField val threads: List<AgentSessionThread>,
  @JvmField val isLoading: Boolean,
  @JvmField val hasLoaded: Boolean,
  @JvmField val hasError: Boolean,
)
