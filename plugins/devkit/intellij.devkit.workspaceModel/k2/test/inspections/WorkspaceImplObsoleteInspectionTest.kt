// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.devkit.workspaceModel.k2.inspections

import com.intellij.devkit.workspaceModel.inspections.WorkspaceImplObsoleteInspectionBaseTest
import com.intellij.openapi.util.IntellijInternalApi

class WorkspaceImplObsoleteInspectionTest : WorkspaceImplObsoleteInspectionBaseTest() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(WorkspaceImplObsoleteInspection())
  }
}