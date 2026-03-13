// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.json.filebacked

import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

data class FileBackedSessionChangeSet(
  // Known files that must be reparsed regardless of file stat heuristics.
  @JvmField val changedPaths: Set<Path> = emptySet(),

  // Overflow/ambiguous events where file-level attribution is not reliable.
  @JvmField val requiresFullRescan: Boolean = false,

  // `changedPaths.isEmpty() && !requiresFullRescan` means "refresh ping":
  // re-run stat-based scan without forcing full reparse.
)

fun normalizeFileBackedSessionPath(path: Path): Path {
  return runCatching {
    path.toAbsolutePath().normalize()
  }.getOrElse {
    path.normalize()
  }
}

fun toFileBackedSessionPathKey(path: Path): String {
  return normalizeFileBackedSessionPath(path).invariantSeparatorsPathString
}
