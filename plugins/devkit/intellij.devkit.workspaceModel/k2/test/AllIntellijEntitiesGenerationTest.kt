// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k2

import com.intellij.devkit.workspaceModel.AbstractAllIntellijEntitiesGenerationTest
import com.intellij.idea.IJIgnore
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class AllIntellijEntitiesGenerationTest : AbstractAllIntellijEntitiesGenerationTest() {
  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K2

  @IJIgnore(issue = "KTIJ-34846")
  override fun `test generation of all entities in intellij codebase`() {
    super.`test generation of all entities in intellij codebase`()
  }
}
