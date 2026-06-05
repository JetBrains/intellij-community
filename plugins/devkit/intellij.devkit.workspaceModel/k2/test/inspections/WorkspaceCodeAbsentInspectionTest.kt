// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k2.inspections

import com.intellij.devkit.workspaceModel.inspections.WorkspaceCodeAbsentInspectionBaseTest
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class WorkspaceCodeAbsentInspectionTest : WorkspaceCodeAbsentInspectionBaseTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(WorkspaceCodeAbsentInspection())
  }
}