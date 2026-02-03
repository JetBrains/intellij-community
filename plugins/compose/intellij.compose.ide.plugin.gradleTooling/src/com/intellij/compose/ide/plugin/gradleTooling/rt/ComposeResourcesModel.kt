// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.gradleTooling.rt

import java.io.Serializable

interface ComposeResourcesModel : Serializable {
  val customComposeResourcesDirs: Map<String, Pair<String, Boolean>>
  val isPublicResClass: Boolean
  val nameOfResClass: String
}

data class ComposeResourcesModelImpl(
  override val customComposeResourcesDirs: Map<String, Pair<String, Boolean>> = emptyMap(),
  override val isPublicResClass: Boolean = false,
  override val nameOfResClass: String = "Res"
) : ComposeResourcesModel