// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The tests of [WorkspaceInheritanceInspection]. Each subclass runs them with the project fixture that it gives to the constructor.
 */
abstract class WorkspaceInheritanceInspectionBaseTest(projectFixture: TestFixture<LightJavaProject>) {
  protected val workspace by workspaceInspectionFixture(projectFixture)

  @BeforeEach
  fun enableWorkspaceInheritanceInspection() {
    workspace.fixture.enableInspections(WorkspaceInheritanceInspection())
  }

  @Test
  fun testInheritance() {
    workspace.doTest()
  }

  @Test
  fun testNotWorkspaceAbstract() {
    workspace.doTest()
  }

  @Test
  fun testNotWorkspaceClasses() {
    workspace.doTest()
  }
}