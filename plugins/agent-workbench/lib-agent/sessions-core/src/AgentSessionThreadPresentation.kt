// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

private const val COMPACT_AGENT_SESSION_TITLE_LENGTH = 50
private val THREAD_TITLE_WHITESPACE = Regex("\\s+")

fun normalizeAgentSessionTitle(title: String?): @NlsSafe String? {
  return title
    ?.replace('\n', ' ')
    ?.replace('\r', ' ')
    ?.replace(THREAD_TITLE_WHITESPACE, " ")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
}

fun formatCompactAgentSessionTitle(
  title: String,
  maxLength: Int = COMPACT_AGENT_SESSION_TITLE_LENGTH,
): @NlsSafe String {
  return StringUtil.trimMiddle(title, maxLength)
}

fun formatCompactAgentSessionThreadTitle(
  threadId: String,
  title: String,
  fallbackTitle: (idPrefix: String) -> String,
  maxLength: Int = COMPACT_AGENT_SESSION_TITLE_LENGTH,
): @NlsSafe String {
  return formatCompactAgentSessionTitle(
    title = formatAgentSessionThreadTitle(threadId = threadId, title = title, fallbackTitle = fallbackTitle),
    maxLength = maxLength,
  )
}

fun formatAgentSessionThreadTitle(
  threadId: String,
  title: String,
  fallbackTitle: (idPrefix: String) -> String,
): @NlsSafe String {
  val normalized = normalizeAgentSessionTitle(title)
  if (normalized != null) {
    return normalized
  }
  val idPrefix = threadId.trim().takeIf { it.isNotEmpty() }?.take(8) ?: "unknown"
  return fallbackTitle(idPrefix)
}

fun formatAgentSessionRelativeTimeShort(
  timestamp: Long,
  now: Long,
  nowLabel: String,
  unknownLabel: String,
): String {
  if (timestamp <= 0L) {
    return unknownLabel
  }

  val absSeconds = abs(((timestamp - now) / 1000.0).roundToLong())
  if (absSeconds < 60) {
    return nowLabel
  }
  if (absSeconds < 60 * 60) {
    val value = max(1, (absSeconds / 60.0).roundToLong())
    return "${value}m"
  }
  if (absSeconds < 60 * 60 * 24) {
    val value = max(1, (absSeconds / (60.0 * 60.0)).roundToLong())
    return "${value}h"
  }
  if (absSeconds < 60 * 60 * 24 * 7) {
    val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0)).roundToLong())
    return "${value}d"
  }
  if (absSeconds < 60 * 60 * 24 * 30) {
    val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0 * 7.0)).roundToLong())
    return "${value}w"
  }
  if (absSeconds < 60 * 60 * 24 * 365) {
    val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0 * 30.0)).roundToLong())
    return "${value}mo"
  }
  val value = max(1, (absSeconds / (60.0 * 60.0 * 24.0 * 365.0)).roundToLong())
  return "${value}y"
}
