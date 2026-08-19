// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import org.jetbrains.idea.devkit.inspections.LightProjectDescriptorEqualsHashCodeInspection

abstract class LightProjectDescriptorEqualsHashCodeInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(LightProjectDescriptorEqualsHashCodeInspection())
    myFixture.addClass(
      """
      package com.intellij.testFramework;
      public class LightProjectDescriptor {}
      """.trimIndent()
    )
    myFixture.addClass(
      """
      package com.intellij.testFramework.fixtures;
      import com.intellij.testFramework.LightProjectDescriptor;
      public class DefaultLightProjectDescriptor extends LightProjectDescriptor {}
      """.trimIndent()
    )
  }
}
