// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k1

import com.intellij.devkit.workspaceModel.AbstractAllIntellijEntitiesGenerationTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class AllIntellijEntitiesGenerationTest : AbstractAllIntellijEntitiesGenerationTest() {
  override val pluginMode: KotlinPluginMode
    get() = KotlinPluginMode.K1
}
