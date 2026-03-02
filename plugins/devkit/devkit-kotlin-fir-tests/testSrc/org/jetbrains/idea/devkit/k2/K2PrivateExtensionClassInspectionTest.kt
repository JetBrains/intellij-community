// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2

import org.jetbrains.idea.devkit.k2.inspections.K2PrivateExtensionClassInspectionTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2PrivateExtensionClassInspectionTest : K2PrivateExtensionClassInspectionTestBase() {

  fun testExtensionClassWithPrivateModifier() {
    doTest()
  }

  fun testActionClassWithPrivateModifier() {
    doTest()
  }

  fun testServiceImplWithPrivateModifier() {
    doTest()
  }
}