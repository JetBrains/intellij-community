// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.ide

internal data class CodexIdeContext(
  @JvmField val activeFile: CodexIdeActiveFile?,
  @JvmField val openTabs: List<CodexIdeFileDescriptor>,
)

internal data class CodexIdeActiveFile(
  @JvmField val label: String,
  @JvmField val path: String,
  @JvmField val fsPath: String,
  @JvmField val selection: CodexIdeRange,
  @JvmField val activeSelectionContent: String,
  @JvmField val selections: List<CodexIdeRange>,
)

internal data class CodexIdeFileDescriptor(
  @JvmField val label: String,
  @JvmField val path: String,
  @JvmField val fsPath: String,
)

internal data class CodexIdeRange(
  @JvmField val start: CodexIdePosition,
  @JvmField val end: CodexIdePosition,
)

internal data class CodexIdePosition(
  @JvmField val line: Int,
  @JvmField val character: Int,
)
