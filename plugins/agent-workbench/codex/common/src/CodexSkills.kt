// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import androidx.compose.runtime.Immutable

@Immutable
data class CodexSkill(
  @JvmField val name: String,
  @JvmField val path: String? = null,
  @JvmField val description: String? = null,
  @JvmField val enabled: Boolean = true,
  @JvmField val displayName: String? = null,
  @JvmField val shortDescription: String? = null,
  @JvmField val defaultPrompt: String? = null,
)
