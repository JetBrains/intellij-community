// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.statistics

// @spec community/plugins/agent-workbench/spec/agent-workbench-telemetry.spec.md

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchError
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchRequest
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchResult
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EnumEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
enum class AgentWorkbenchEntryPoint {
  PROMPT,
  TREE_ROW,
  TREE_ROW_OVERLAY,
  TREE_POPUP,
  EDITOR_TAB_QUICK,
  EDITOR_TAB_POPUP,
  TOOLBAR,
  WINDOW_MENU,
}

@ApiStatus.Internal
enum class AgentWorkbenchTelemetryProvider {
  CODEX,
  CLAUDE,
  OTHER,
}

@ApiStatus.Internal
enum class AgentWorkbenchTargetKind {
  NEW_THREAD,
  THREAD,
  SUB_AGENT,
}

@ApiStatus.Internal
enum class AgentWorkbenchPromptBlockedReason {
  EMPTY_PROMPT,
  NO_PROVIDERS,
  PROVIDER_UNAVAILABLE,
  PROJECT_PATH,
  NO_LAUNCHER,
  EXISTING_TASK_NOT_SELECTED,
  OTHER,
}

@ApiStatus.Internal
enum class AgentWorkbenchPromptLaunchResultKind {
  SUCCESS,
  PROVIDER_UNAVAILABLE,
  UNSUPPORTED_LAUNCH_MODE,
  TARGET_THREAD_NOT_FOUND,
  CANCELLED,
  DROPPED_DUPLICATE,
  INTERNAL_ERROR,
}

@ApiStatus.Internal
data class AgentWorkbenchTelemetryEvent(
  @JvmField val id: String,
  @JvmField val entryPoint: AgentWorkbenchEntryPoint? = null,
  @JvmField val provider: AgentWorkbenchTelemetryProvider? = null,
  @JvmField val launchMode: AgentSessionLaunchMode? = null,
  @JvmField val targetKind: AgentWorkbenchTargetKind? = null,
  @JvmField val blockedReason: AgentWorkbenchPromptBlockedReason? = null,
  @JvmField val launchResult: AgentWorkbenchPromptLaunchResultKind? = null,
)

@ApiStatus.Internal
object AgentWorkbenchTelemetry {
  const val PROMPT_SUBMIT_BLOCKED_EVENT_ID: String = "prompt.submit_blocked"
  const val PROMPT_LAUNCH_RESOLVED_EVENT_ID: String = "prompt.launch_resolved"
  const val THREAD_CREATE_REQUESTED_EVENT_ID: String = "thread.create_requested"
  const val THREAD_OPEN_REQUESTED_EVENT_ID: String = "thread.open_requested"
  const val THREAD_ARCHIVE_REQUESTED_EVENT_ID: String = "thread.archive_requested"
  const val PROJECT_FOCUS_REQUESTED_EVENT_ID: String = "project.focus_requested"
  const val DEDICATED_FRAME_FOCUS_REQUESTED_EVENT_ID: String = "dedicated_frame.focus_requested"

  private val testHandlerRef = AtomicReference<((AgentWorkbenchTelemetryEvent) -> Unit)?>(null)

  fun logPromptSubmitBlocked(
    validationErrorKey: String,
    provider: AgentSessionProvider?,
    launchMode: AgentSessionLaunchMode,
  ) {
    emit(
      AgentWorkbenchTelemetryEvent(
        id = PROMPT_SUBMIT_BLOCKED_EVENT_ID,
        provider = mapProvider(provider),
        launchMode = launchMode,
        blockedReason = mapBlockedReason(validationErrorKey),
      )
    )
  }

  fun logPromptLaunchResolved(request: AgentPromptLaunchRequest, result: AgentPromptLaunchResult) {
    emit(
      AgentWorkbenchTelemetryEvent(
        id = PROMPT_LAUNCH_RESOLVED_EVENT_ID,
        provider = mapProvider(request.provider),
        launchMode = request.launchMode,
        targetKind = if (request.targetThreadId == null) AgentWorkbenchTargetKind.NEW_THREAD else AgentWorkbenchTargetKind.THREAD,
        launchResult = mapLaunchResult(result),
      )
    )
  }

  fun logThreadCreateRequested(
    entryPoint: AgentWorkbenchEntryPoint,
    provider: AgentSessionProvider,
    launchMode: AgentSessionLaunchMode,
  ) {
    emit(
      AgentWorkbenchTelemetryEvent(
        id = THREAD_CREATE_REQUESTED_EVENT_ID,
        entryPoint = entryPoint,
        provider = mapProvider(provider),
        launchMode = launchMode,
        targetKind = AgentWorkbenchTargetKind.NEW_THREAD,
      )
    )
  }

  fun logThreadOpenRequested(
    entryPoint: AgentWorkbenchEntryPoint,
    provider: AgentSessionProvider,
    targetKind: AgentWorkbenchTargetKind,
  ) {
    emit(
      AgentWorkbenchTelemetryEvent(
        id = THREAD_OPEN_REQUESTED_EVENT_ID,
        entryPoint = entryPoint,
        provider = mapProvider(provider),
        targetKind = targetKind,
      )
    )
  }

  fun logThreadArchiveRequested(
    entryPoint: AgentWorkbenchEntryPoint,
    provider: AgentSessionProvider?,
  ) {
    emit(
      AgentWorkbenchTelemetryEvent(
        id = THREAD_ARCHIVE_REQUESTED_EVENT_ID,
        entryPoint = entryPoint,
        provider = mapProvider(provider),
      )
    )
  }

  fun logProjectFocusRequested(entryPoint: AgentWorkbenchEntryPoint) {
    emit(
      AgentWorkbenchTelemetryEvent(
        id = PROJECT_FOCUS_REQUESTED_EVENT_ID,
        entryPoint = entryPoint,
      )
    )
  }

  fun logDedicatedFrameFocusRequested(entryPoint: AgentWorkbenchEntryPoint) {
    emit(
      AgentWorkbenchTelemetryEvent(
        id = DEDICATED_FRAME_FOCUS_REQUESTED_EVENT_ID,
        entryPoint = entryPoint,
      )
    )
  }

  @TestOnly
  fun pushTestHandler(handler: (AgentWorkbenchTelemetryEvent) -> Unit): AccessToken {
    val previous = testHandlerRef.getAndSet(handler)
    return object : AccessToken() {
      override fun finish() {
        testHandlerRef.set(previous)
      }
    }
  }

  private fun emit(event: AgentWorkbenchTelemetryEvent) {
    val handler = testHandlerRef.get()
    if (handler != null) {
      handler(event)
      return
    }
    AgentWorkbenchFusCollector.log(event)
  }

  private fun mapProvider(provider: AgentSessionProvider?): AgentWorkbenchTelemetryProvider? {
    return when (provider) {
      null -> null
      AgentSessionProvider.CODEX -> AgentWorkbenchTelemetryProvider.CODEX
      AgentSessionProvider.CLAUDE -> AgentWorkbenchTelemetryProvider.CLAUDE
      else -> AgentWorkbenchTelemetryProvider.OTHER
    }
  }

  private fun mapBlockedReason(validationErrorKey: String): AgentWorkbenchPromptBlockedReason {
    return when (validationErrorKey) {
      "popup.error.empty.prompt" -> AgentWorkbenchPromptBlockedReason.EMPTY_PROMPT
      "popup.error.no.providers" -> AgentWorkbenchPromptBlockedReason.NO_PROVIDERS
      "popup.error.provider.unavailable" -> AgentWorkbenchPromptBlockedReason.PROVIDER_UNAVAILABLE
      "popup.error.project.path" -> AgentWorkbenchPromptBlockedReason.PROJECT_PATH
      "popup.error.no.launcher" -> AgentWorkbenchPromptBlockedReason.NO_LAUNCHER
      "popup.error.existing.select.task" -> AgentWorkbenchPromptBlockedReason.EXISTING_TASK_NOT_SELECTED
      else -> AgentWorkbenchPromptBlockedReason.OTHER
    }
  }

  private fun mapLaunchResult(result: AgentPromptLaunchResult): AgentWorkbenchPromptLaunchResultKind {
    if (result.launched) {
      return AgentWorkbenchPromptLaunchResultKind.SUCCESS
    }
    return when (result.error) {
      AgentPromptLaunchError.PROVIDER_UNAVAILABLE -> AgentWorkbenchPromptLaunchResultKind.PROVIDER_UNAVAILABLE
      AgentPromptLaunchError.UNSUPPORTED_LAUNCH_MODE -> AgentWorkbenchPromptLaunchResultKind.UNSUPPORTED_LAUNCH_MODE
      AgentPromptLaunchError.TARGET_THREAD_NOT_FOUND -> AgentWorkbenchPromptLaunchResultKind.TARGET_THREAD_NOT_FOUND
      AgentPromptLaunchError.CANCELLED -> AgentWorkbenchPromptLaunchResultKind.CANCELLED
      AgentPromptLaunchError.DROPPED_DUPLICATE -> AgentWorkbenchPromptLaunchResultKind.DROPPED_DUPLICATE
      AgentPromptLaunchError.INTERNAL_ERROR,
      null -> AgentWorkbenchPromptLaunchResultKind.INTERNAL_ERROR
    }
  }
}

internal object AgentWorkbenchFusCollector : CounterUsagesCollector() {
  private val LOG = logger<AgentWorkbenchFusCollector>()

  private val group = EventLogGroup("agent.workbench", 1)

  private val entryPointField: EnumEventField<AgentWorkbenchEntryPoint> = EventFields.Enum("entry_point", AgentWorkbenchEntryPoint::class.java)
  private val providerField: EnumEventField<AgentWorkbenchTelemetryProvider> =
    EventFields.Enum("provider", AgentWorkbenchTelemetryProvider::class.java)
  private val launchModeField: EnumEventField<AgentSessionLaunchMode> =
    EventFields.Enum("launch_mode", AgentSessionLaunchMode::class.java)
  private val targetKindField: EnumEventField<AgentWorkbenchTargetKind> =
    EventFields.Enum("target_kind", AgentWorkbenchTargetKind::class.java)
  private val blockedReasonField: EnumEventField<AgentWorkbenchPromptBlockedReason> =
    EventFields.Enum("blocked_reason", AgentWorkbenchPromptBlockedReason::class.java)
  private val launchResultField: EnumEventField<AgentWorkbenchPromptLaunchResultKind> =
    EventFields.Enum("launch_result", AgentWorkbenchPromptLaunchResultKind::class.java)

  private val promptSubmitBlocked: VarargEventId = group.registerVarargEvent(
    AgentWorkbenchTelemetry.PROMPT_SUBMIT_BLOCKED_EVENT_ID,
    providerField,
    launchModeField,
    blockedReasonField,
  )
  private val promptLaunchResolved: VarargEventId = group.registerVarargEvent(
    AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID,
    providerField,
    launchModeField,
    targetKindField,
    launchResultField,
  )
  private val threadCreateRequested: VarargEventId = group.registerVarargEvent(
    AgentWorkbenchTelemetry.THREAD_CREATE_REQUESTED_EVENT_ID,
    entryPointField,
    providerField,
    launchModeField,
    targetKindField,
  )
  private val threadOpenRequested: VarargEventId = group.registerVarargEvent(
    AgentWorkbenchTelemetry.THREAD_OPEN_REQUESTED_EVENT_ID,
    entryPointField,
    providerField,
    targetKindField,
  )
  private val threadArchiveRequested: VarargEventId = group.registerVarargEvent(
    AgentWorkbenchTelemetry.THREAD_ARCHIVE_REQUESTED_EVENT_ID,
    entryPointField,
    providerField,
  )
  private val projectFocusRequested: VarargEventId = group.registerVarargEvent(
    AgentWorkbenchTelemetry.PROJECT_FOCUS_REQUESTED_EVENT_ID,
    entryPointField,
  )
  private val dedicatedFrameFocusRequested: VarargEventId = group.registerVarargEvent(
    AgentWorkbenchTelemetry.DEDICATED_FRAME_FOCUS_REQUESTED_EVENT_ID,
    entryPointField,
  )

  override fun getGroup(): EventLogGroup = group

  fun log(event: AgentWorkbenchTelemetryEvent) {
    when (event.id) {
      AgentWorkbenchTelemetry.PROMPT_SUBMIT_BLOCKED_EVENT_ID -> promptSubmitBlocked.log(buildList {
        event.provider?.let { add(providerField.with(it)) }
        event.launchMode?.let { add(launchModeField.with(it)) }
        event.blockedReason?.let { add(blockedReasonField.with(it)) }
      })

      AgentWorkbenchTelemetry.PROMPT_LAUNCH_RESOLVED_EVENT_ID -> promptLaunchResolved.log(buildList {
        event.provider?.let { add(providerField.with(it)) }
        event.launchMode?.let { add(launchModeField.with(it)) }
        event.targetKind?.let { add(targetKindField.with(it)) }
        event.launchResult?.let { add(launchResultField.with(it)) }
      })

      AgentWorkbenchTelemetry.THREAD_CREATE_REQUESTED_EVENT_ID -> threadCreateRequested.log(buildList {
        event.entryPoint?.let { add(entryPointField.with(it)) }
        event.provider?.let { add(providerField.with(it)) }
        event.launchMode?.let { add(launchModeField.with(it)) }
        event.targetKind?.let { add(targetKindField.with(it)) }
      })

      AgentWorkbenchTelemetry.THREAD_OPEN_REQUESTED_EVENT_ID -> threadOpenRequested.log(buildList {
        event.entryPoint?.let { add(entryPointField.with(it)) }
        event.provider?.let { add(providerField.with(it)) }
        event.targetKind?.let { add(targetKindField.with(it)) }
      })

      AgentWorkbenchTelemetry.THREAD_ARCHIVE_REQUESTED_EVENT_ID -> threadArchiveRequested.log(buildList {
        event.entryPoint?.let { add(entryPointField.with(it)) }
        event.provider?.let { add(providerField.with(it)) }
      })

      AgentWorkbenchTelemetry.PROJECT_FOCUS_REQUESTED_EVENT_ID -> projectFocusRequested.log(buildList {
        event.entryPoint?.let { add(entryPointField.with(it)) }
      })

      AgentWorkbenchTelemetry.DEDICATED_FRAME_FOCUS_REQUESTED_EVENT_ID -> dedicatedFrameFocusRequested.log(buildList {
        event.entryPoint?.let { add(entryPointField.with(it)) }
      })

      else -> LOG.error("Unknown telemetry event id: ${event.id}")
    }
  }
}
