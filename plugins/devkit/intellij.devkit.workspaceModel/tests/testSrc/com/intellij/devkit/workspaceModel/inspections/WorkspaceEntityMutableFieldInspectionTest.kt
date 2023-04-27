// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.devkit.workspaceModel.WorkspaceEntityMutableFieldInspection

class WorkspaceEntityMutableFieldInspectionTest: WorkspaceEntityInspectionBase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(WorkspaceEntityMutableFieldInspection())
  }

  fun testVarFieldForbidden() {
    doTest("Change to 'val'")
  }
}