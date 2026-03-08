// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k1

import com.intellij.devkit.workspaceModel.AbstractAllIntellijEntitiesGenerationTest
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.kotlin.idea.test.UseK1PluginMode
import org.junit.jupiter.api.Disabled

@UseK1PluginMode
@TestApplication
@Disabled("AT-3959")
class AllIntellijEntitiesGenerationTest : AbstractAllIntellijEntitiesGenerationTest()
