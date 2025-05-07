// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.gradleTooling.rt

import java.io.Serializable

interface ComposeResourcesModel : Serializable {
  val customComposeResourcesDirs: Map<String, String>
}

data class ComposeResourcesModelImpl(
  override val customComposeResourcesDirs: Map<String, String> = emptyMap(),
) : ComposeResourcesModel