// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

abstract class IncorrectServiceRetrievingInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(IncorrectServiceRetrievingInspection())
    myFixture.addClass(
      """
      package com.intellij.openapi.components;

      public interface ComponentManager {
        <T> T getService(@NotNull Class<T> serviceClass);
        <T> T getServiceIfCreated(@NotNull Class<T> serviceClass);
      }
      """)
    myFixture.addClass(
      """
      package com.intellij.openapi.project;

      import com.intellij.openapi.components.ComponentManager;

      public interface Project extends ComponentManager {}
      """)
    myFixture.addClass(
      """
      package com.intellij.openapi.application;

      public final class ApplicationManager {
        private static Application ourApplication;

        public static Application getApplication() {
          return ourApplication;
        }      
      }
      """)
    myFixture.addClass(
      """
      package com.intellij.openapi.application;

      import com.intellij.openapi.components.ComponentManager;

      public interface Application extends ComponentManager {}
      """)
    myFixture.addClass(
      """
      package kotlin.reflect;

      public class KClass<T> {
        public Class<T> java;
      }
      """)
  }

  protected fun doTest(isAppLevelDefault: Boolean = true) {
    if (isAppLevelDefault) {
      myFixture.addClass("""
      package com.intellij.openapi.components;

      public @interface Service {
        Level[] value() default Level.APP;

        enum Level { APP, PROJECT }
      }
      """)
    }
    else {
      myFixture.addClass("""
      package com.intellij.openapi.components;

      public @interface Service {
        Level[] value() default {};

        enum Level { APP, PROJECT }
      }
      """)
    }
    doTest()
  }
}
