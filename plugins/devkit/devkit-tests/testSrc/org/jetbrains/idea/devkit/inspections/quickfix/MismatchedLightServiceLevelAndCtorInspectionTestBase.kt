// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import org.jetbrains.idea.devkit.inspections.MismatchedLightServiceLevelAndCtorInspection

abstract class MismatchedLightServiceLevelAndCtorInspectionTestBase : DevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(MismatchedLightServiceLevelAndCtorInspection())
    myFixture.addClass("""
      package com.intellij.openapi.components;

      public @interface Service {
        Level[] value() default {};

        enum Level { APP, PROJECT }
      }
      """)
    myFixture.addClass("""
      package com.intellij.openapi.project;

      import com.intellij.openapi.components.ComponentManager;

      public interface Project extends ComponentManager {}
      """)
    myFixture.addClass("""
      package kotlinx.coroutines;

      public interface CoroutineScope {}
    """)
  }
}
