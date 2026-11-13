// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.workspaceModel.k2.inspections

import com.intellij.devkit.workspaceModel.inspections.WorkspaceImplObsoleteInspection
import com.intellij.devkit.workspaceModel.inspections.WorkspaceImplObsoleteInspectionBaseTest
import com.intellij.devkit.workspaceModel.inspections.workspaceInspectionProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach

@TestApplication
class WorkspaceImplObsoleteInspectionTest : WorkspaceImplObsoleteInspectionBaseTest(projectFixture) {
  companion object {
    private val projectFixture = workspaceInspectionProjectFixture()
  }

  @BeforeEach
  fun enableWorkspaceImplObsoleteInspection() {
    workspace.fixture.enableInspections(WorkspaceImplObsoleteInspection())
  }
}