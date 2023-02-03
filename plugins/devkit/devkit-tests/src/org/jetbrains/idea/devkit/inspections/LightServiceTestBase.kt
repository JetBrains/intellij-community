// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.inspections.quickfix.DevKitInspectionFixTestBase

abstract class LightServiceTestBase : DevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(LightServiceInspection())
    myFixture.addClass("""
      package com.intellij.openapi.components;

      public @interface Service {
        Level[] value() default {};

        enum Level {
          APP, PROJECT
        }
      }
      """)
    myFixture.addClass("""
      package org.jetbrains.annotations;

      public @interface NotNull {}
      """)
    myFixture.addClass("""
      package com.intellij.openapi.project;

      public interface Project extends ComponentManager {}
      """)
    myFixture.addClass("""
      package kotlinx.coroutines;

      public interface CoroutineScope {}
    """.trimIndent())
  }
}

