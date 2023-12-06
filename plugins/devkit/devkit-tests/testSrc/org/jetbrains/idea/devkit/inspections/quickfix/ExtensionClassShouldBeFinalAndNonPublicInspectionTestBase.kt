// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import org.jetbrains.idea.devkit.inspections.ExtensionClassShouldBeFinalAndNonPublicInspection

abstract class ExtensionClassShouldBeFinalAndNonPublicInspectionTestBase : LightDevKitInspectionFixTestBase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ExtensionClassShouldBeFinalAndNonPublicInspection())
    myFixture.addClass(
      """
      package org.jetbrains.annotations;

      public @interface VisibleForTesting { }
    """)
  }
}