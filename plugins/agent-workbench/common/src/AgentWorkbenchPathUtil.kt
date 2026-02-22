// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

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
