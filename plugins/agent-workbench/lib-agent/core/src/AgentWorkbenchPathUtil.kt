// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.core

import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

fun parseAgentWorkbenchPathOrNull(path: String): Path? {
  return try {
    Path.of(path)
  }
  catch (_: InvalidPathException) {
    null
  }
}

fun normalizeAgentWorkbenchPath(path: String): String {
  return parseAgentWorkbenchPathOrNull(path)?.invariantSeparatorsPathString ?: path
}

fun normalizeAgentWorkbenchPathOrNull(path: String): String? {
  return parseAgentWorkbenchPathOrNull(path)?.invariantSeparatorsPathString
}

fun normalizeAgentSessionProjectPath(path: String): String? {
  val trimmedPath = path.trim().takeIf { it.isNotEmpty() } ?: return null
  val normalizedPath = normalizeAgentWorkbenchPathOrNull(trimmedPath) ?: return null
  return normalizedPath.trimEnd('/').ifEmpty { "/" }
}

fun normalizeAgentSessionTitle(title: String?): String? {
  return title
    ?.replace('\n', ' ')
    ?.replace('\r', ' ')
    ?.replace(AGENT_SESSION_TITLE_WHITESPACE, " ")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
}

private val AGENT_SESSION_TITLE_WHITESPACE = Regex("\\s+")
