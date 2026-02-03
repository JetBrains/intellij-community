// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.devkit.workspaceModel.inspections

import com.intellij.openapi.util.IntellijInternalApi

abstract class WorkspaceEntityMutableFieldInspectionBaseTest : WorkspaceInspectionBaseTest() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(WorkspaceEntityMutableFieldInspection())
  }

  fun testVarFieldForbidden() {
    doTestWithQuickFix("Change to 'val'")
  }
  
  fun testNotWorkspaceClasses() {
    doTest()
  }
}