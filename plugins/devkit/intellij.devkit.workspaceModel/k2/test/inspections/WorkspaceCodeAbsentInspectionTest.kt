// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.devkit.workspaceModel.k2.inspections

import com.intellij.devkit.workspaceModel.inspections.WorkspaceCodeAbsentInspectionBaseTest
import com.intellij.openapi.util.IntellijInternalApi

class WorkspaceCodeAbsentInspectionTest : WorkspaceCodeAbsentInspectionBaseTest() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(WorkspaceCodeAbsentInspection())
  }
}