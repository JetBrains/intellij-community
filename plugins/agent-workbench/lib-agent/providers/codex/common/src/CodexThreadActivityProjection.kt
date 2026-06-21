// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CodexThreadActivityProjection {
  private var latestUserMessageOrder: Long = Long.MIN_VALUE
  private var latestAssistantMessageOrder: Long = Long.MIN_VALUE
  private var pendingPlan: PendingSignal? = null
  private var reviewing: Boolean = false
  private var nextSyntheticUserInputId: Int = 0
  private var nextSyntheticApprovalId: Int = 0
  private var nextSyntheticToolCallId: Int = 0
  private var nextSyntheticTurnId: Int = 0

  private val pendingUserInputByCallId = LinkedHashMap<String, Long>()
  private val pendingApprovalByCallId = LinkedHashMap<String, PendingSignal>()
  private val pendingToolCallByCallId = LinkedHashMap<String, PendingSignal>()
  private val inProgressTurnById = LinkedHashMap<String, PendingSignal>()

  fun apply(signal: CodexThreadActivitySignal) {
    when (signal) {
      is CodexThreadActivitySignal.UserMessage -> markUserMessage(signal.order)
      is CodexThreadActivitySignal.AssistantMessage -> markAssistantMessage(signal.order)
      is CodexThreadActivitySignal.Plan -> markPlan(order = signal.order, turnId = signal.turnId)
      is CodexThreadActivitySignal.TurnStarted -> markTurnStarted(order = signal.order, turnId = signal.turnId)
      is CodexThreadActivitySignal.TurnCompleted -> markTurnCompleted(order = signal.order, turnId = signal.turnId)
      is CodexThreadActivitySignal.PendingUserInput -> markPendingUserInput(order = signal.order, callId = signal.callId)
      is CodexThreadActivitySignal.ClearPendingUserInput -> clearPendingUserInput(signal.callId)
      is CodexThreadActivitySignal.PendingApproval -> markPendingApproval(order = signal.order, callId = signal.callId, turnId = signal.turnId)
      is CodexThreadActivitySignal.ClearPendingApproval -> clearPendingApproval(callId = signal.callId, turnId = signal.turnId)
      is CodexThreadActivitySignal.ToolCallStarted -> markToolCallStarted(order = signal.order, callId = signal.callId, turnId = signal.turnId)
      is CodexThreadActivitySignal.ClearToolCall -> clearToolCall(callId = signal.callId, turnId = signal.turnId)
      CodexThreadActivitySignal.ReviewModeEntered -> enterReviewMode()
      CodexThreadActivitySignal.ReviewModeExited -> exitReviewMode()
    }
  }

  fun markUserMessage(order: Long) {
    latestUserMessageOrder = maxOf(latestUserMessageOrder, order)
    if (pendingPlan != null && order >= pendingPlan!!.order) {
      pendingPlan = null
    }
    pendingUserInputByCallId.entries.removeIf { (_, pendingOrder) -> order >= pendingOrder }
  }

  fun markAssistantMessage(order: Long) {
    latestAssistantMessageOrder = maxOf(latestAssistantMessageOrder, order)
  }

  fun markPlan(order: Long, turnId: String? = null) {
    val currentPlan = pendingPlan
    if (currentPlan == null || order >= currentPlan.order) {
      pendingPlan = PendingSignal(order = order, turnId = turnId)
    }
  }

  fun markTurnStarted(order: Long, turnId: String? = null) {
    inProgressTurnById[turnKey(turnId)] = PendingSignal(order = order, turnId = turnId)
  }

  fun markTurnCompleted(order: Long, turnId: String? = null) {
    clearSignalsForTurn(inProgressTurnById, completedOrder = order, completedTurnId = turnId)
    clearSignalsForTurn(pendingApprovalByCallId, completedOrder = order, completedTurnId = turnId)
    clearSignalsForTurn(pendingToolCallByCallId, completedOrder = order, completedTurnId = turnId)
    val plan = pendingPlan
    if (plan != null && shouldClearSignalForTurn(plan, completedOrder = order, completedTurnId = turnId)) {
      pendingPlan = null
    }
  }

  fun markPendingUserInput(order: Long, callId: String? = null) {
    pendingUserInputByCallId.merge(callId ?: "pending-user-input-${nextSyntheticUserInputId++}", order, ::maxOf)
  }

  fun clearPendingUserInput(callId: String?) {
    if (callId != null) {
      pendingUserInputByCallId.remove(callId)
    }
    else if (pendingUserInputByCallId.size == 1) {
      pendingUserInputByCallId.clear()
    }
  }

  fun markPendingApproval(order: Long, callId: String? = null, turnId: String? = null) {
    val resolvedCallId = callId ?: "pending-approval-${nextSyntheticApprovalId++}"
    val current = pendingApprovalByCallId[resolvedCallId]
    if (current == null || order >= current.order) {
      pendingApprovalByCallId[resolvedCallId] = PendingSignal(order = order, turnId = turnId)
    }
  }

  fun clearPendingApproval(callId: String?, turnId: String? = null) {
    if (callId != null && pendingApprovalByCallId.remove(callId) != null) {
      return
    }
    if (turnId != null) {
      clearSignalsForTurn(pendingApprovalByCallId, completedOrder = Long.MAX_VALUE, completedTurnId = turnId)
    }
    else if (pendingApprovalByCallId.size == 1) {
      pendingApprovalByCallId.clear()
    }
  }

  fun markToolCallStarted(order: Long, callId: String? = null, turnId: String? = null) {
    val resolvedCallId = callId ?: "pending-tool-call-${nextSyntheticToolCallId++}"
    val current = pendingToolCallByCallId[resolvedCallId]
    if (current == null || order >= current.order) {
      pendingToolCallByCallId[resolvedCallId] = PendingSignal(order = order, turnId = turnId)
    }
  }

  fun clearToolCall(callId: String?, turnId: String? = null) {
    if (callId != null && pendingToolCallByCallId.remove(callId) != null) {
      return
    }
    if (turnId != null) {
      clearSignalsForTurn(pendingToolCallByCallId, completedOrder = Long.MAX_VALUE, completedTurnId = turnId)
    }
    else if (pendingToolCallByCallId.size == 1) {
      pendingToolCallByCallId.clear()
    }
  }

  fun enterReviewMode() {
    reviewing = true
  }

  fun exitReviewMode() {
    reviewing = false
  }

  fun toSnapshot(
    threadId: String,
    updatedAt: Long,
    statusKind: CodexThreadStatusKind,
    activeFlags: List<CodexThreadActiveFlag> = emptyList(),
    hasTurnActivity: Boolean = false,
  ): CodexThreadActivitySnapshot {
    val resolvedActiveFlags = LinkedHashSet<CodexThreadActiveFlag>(activeFlags.size + 2)
    resolvedActiveFlags.addAll(activeFlags)
    if (pendingApprovalByCallId.isNotEmpty()) {
      resolvedActiveFlags.add(CodexThreadActiveFlag.WAITING_ON_APPROVAL)
    }
    if (pendingUserInputByCallId.isNotEmpty()) {
      resolvedActiveFlags.add(CodexThreadActiveFlag.WAITING_ON_USER_INPUT)
    }

    return CodexThreadActivitySnapshot(
      threadId = threadId,
      updatedAt = updatedAt,
      statusKind = statusKind,
      activeFlags = ArrayList(resolvedActiveFlags),
      hasUnreadAssistantMessage = latestAssistantMessageOrder > latestUserMessageOrder,
      hasPendingPlan = pendingPlan != null && pendingPlan!!.order > latestUserMessageOrder,
      isReviewing = reviewing,
      hasInProgressTurn = inProgressTurnById.isNotEmpty() || pendingToolCallByCallId.isNotEmpty(),
      hasTurnActivity = hasTurnActivity,
    )
  }

  private fun turnKey(turnId: String?): String {
    return turnId ?: "pending-turn-${nextSyntheticTurnId++}"
  }

  private fun clearSignalsForTurn(
    signalsById: MutableMap<String, PendingSignal>,
    completedOrder: Long,
    completedTurnId: String?,
  ) {
    val iterator = signalsById.entries.iterator()
    while (iterator.hasNext()) {
      val (_, signal) = iterator.next()
      if (shouldClearSignalForTurn(signal, completedOrder = completedOrder, completedTurnId = completedTurnId)) {
        iterator.remove()
      }
    }
  }

  private fun shouldClearSignalForTurn(signal: PendingSignal, completedOrder: Long, completedTurnId: String?): Boolean {
    return when {
      completedTurnId == null -> signal.turnId == null || signal.order <= completedOrder
      signal.turnId == null -> true
      else -> signal.turnId == completedTurnId
    }
  }

  private data class PendingSignal(
    @JvmField val order: Long,
    @JvmField val turnId: String?,
  )
}

@ApiStatus.Internal
sealed interface CodexThreadActivitySignal {
  data class UserMessage(@JvmField val order: Long) : CodexThreadActivitySignal
  data class AssistantMessage(@JvmField val order: Long) : CodexThreadActivitySignal
  data class Plan(@JvmField val order: Long, @JvmField val turnId: String? = null) : CodexThreadActivitySignal
  data class TurnStarted(@JvmField val order: Long, @JvmField val turnId: String? = null) : CodexThreadActivitySignal
  data class TurnCompleted(@JvmField val order: Long, @JvmField val turnId: String? = null) : CodexThreadActivitySignal
  data class PendingUserInput(@JvmField val order: Long, @JvmField val callId: String? = null) : CodexThreadActivitySignal
  data class ClearPendingUserInput(@JvmField val callId: String?) : CodexThreadActivitySignal
  data class PendingApproval(
    @JvmField val order: Long,
    @JvmField val callId: String? = null,
    @JvmField val turnId: String? = null,
  ) : CodexThreadActivitySignal
  data class ClearPendingApproval(@JvmField val callId: String?, @JvmField val turnId: String? = null) : CodexThreadActivitySignal
  data class ToolCallStarted(
    @JvmField val order: Long,
    @JvmField val callId: String? = null,
    @JvmField val turnId: String? = null,
  ) : CodexThreadActivitySignal
  data class ClearToolCall(@JvmField val callId: String?, @JvmField val turnId: String? = null) : CodexThreadActivitySignal
  object ReviewModeEntered : CodexThreadActivitySignal
  object ReviewModeExited : CodexThreadActivitySignal
}
