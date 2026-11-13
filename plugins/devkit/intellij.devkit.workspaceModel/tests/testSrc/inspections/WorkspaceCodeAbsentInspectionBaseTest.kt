// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import org.junit.jupiter.api.Test

/**
 * The tests of `WorkspaceCodeAbsentInspection`. Each subclass runs them with the project fixture that it gives to the constructor.
 */
abstract class WorkspaceCodeAbsentInspectionBaseTest(projectFixture: TestFixture<LightJavaProject>) {
  protected val workspace by workspaceInspectionFixture(projectFixture)

  @Test
  fun testEntityImplementation() {
    workspace.doTest()
  }

  @Test
  fun testEntitySourceMetadata() {
    workspace.doTest()
  }

  @Test
  fun testEntitySourceFakeMetadata() {
    workspace.doTest()
  }

  @Test
  fun testNotWorkspaceClasses() {
    workspace.doTest()
  }
}