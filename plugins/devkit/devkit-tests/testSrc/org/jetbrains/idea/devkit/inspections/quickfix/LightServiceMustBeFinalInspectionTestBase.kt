// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import org.jetbrains.idea.devkit.inspections.LightServiceMustBeFinalInspection

abstract class LightServiceMustBeFinalInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(LightServiceMustBeFinalInspection())
    myFixture.addClass(
      """
      package com.intellij.openapi.components;

      public @interface Service {}
      """)
  }
}
