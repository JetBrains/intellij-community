// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("unused")

package com.intellij.agent.workbench.engine.core

import kotlinx.serialization.Serializable

/**
 * Stable identifier of a single agent thread (a task/run hosted by Engine).
 *
 * For the legacy bridge the value mirrors [com.intellij.platform.ai.agent.core] thread identity
 * ("<provider>:<threadId>"), but Engine code must treat it as an opaque string.
 */
@Serializable
@JvmInline
value class ThreadId(val value: String)

/** Identifier of a registered structured [AgentRuntime]. */
@Serializable
@JvmInline
value class RuntimeId(val value: String)

/** Monotonic-per-thread identifier of a single persisted event. */
@Serializable
@JvmInline
value class EventId(val value: String)

/** Identifier of a tool call reported by a runtime. */
@Serializable
@JvmInline
value class ToolCallId(val value: String)

/** Identifier of an approval request raised by a runtime. */
@Serializable
@JvmInline
value class ApprovalRequestId(val value: String)

/** Identifier of a computed change set (diff) attributed to a thread. */
@Serializable
@JvmInline
value class ChangeSetId(val value: String)

/** Identifier of a checkpoint created for a thread. */
@Serializable
@JvmInline
value class CheckpointId(val value: String)
