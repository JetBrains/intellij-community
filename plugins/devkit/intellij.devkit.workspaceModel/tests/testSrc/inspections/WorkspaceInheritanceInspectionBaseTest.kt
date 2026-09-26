// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

abstract class WorkspaceInheritanceInspectionBaseTest : WorkspaceInspectionBaseTest() {
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