// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.devkit.workspaceModel.inspections

import com.intellij.openapi.util.IntellijInternalApi

abstract class WorkspaceCodeAbsentInspectionBaseTest : WorkspaceInspectionBaseTest() {
  fun testEntityImplementation() {
    doTest()
  }

  fun testEntitySourceMetadata() {
    doTest()
  }

  fun testEntitySourceFakeMetadata() {
    doTest()
  }

  fun testNotWorkspaceClasses() {
    doTest()
  }
}