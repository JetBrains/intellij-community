// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.k2.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.compose.ide.plugin.shared.inspections.ComposeMissingPluginInspectionTest
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import org.jetbrains.plugins.gradle.util.GradleConstants

internal class K2GradleComposeMissingPluginInspectionTest : ComposeMissingPluginInspectionTest() {

  override fun setUp() {
    super.setUp()
    ExternalSystemModulePropertyManager.getInstance(myFixture.module).setExternalId(GradleConstants.SYSTEM_ID)
    myFixture.enableInspections(K2ComposeMissingPluginInspection() as InspectionProfileEntry)
  }
}