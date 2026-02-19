// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.devkit.workspaceModel.k1.inspections

import com.intellij.devkit.workspaceModel.inspections.WorkspaceInheritanceInspection
import com.intellij.devkit.workspaceModel.inspections.WorkspaceInspectionBaseTest
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class WorkspaceInheritanceInspectionTest : WorkspaceInspectionBaseTest() {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(WorkspaceInheritanceInspection())
  }

  fun testInheritance() {
    doTest()
  }
  
  fun testNotWorkspaceAbstract() {
    doTest()
  }
  
  fun testNotWorkspaceClasses() {
    doTest()
  }
}