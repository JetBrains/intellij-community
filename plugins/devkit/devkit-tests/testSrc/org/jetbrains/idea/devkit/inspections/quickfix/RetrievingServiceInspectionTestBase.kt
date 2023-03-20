// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import org.jetbrains.idea.devkit.inspections.RetrievingServiceInspection

abstract class RetrievingServiceInspectionTestBase : DevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(RetrievingServiceInspection())
    myFixture.addClass("""
      package com.intellij.openapi.components;

      public @interface Service {
        Level[] value() default {};

        enum Level { APP, PROJECT }
      }
      """)
    myFixture.addClass("""
      package com.intellij.openapi.components;

      public interface ComponentManager {
        <T> T getService(@NotNull Class<T> serviceClass);
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
    myFixture.addClass("""
      package com.intellij.openapi.application;

      public class ApplicationManager {
        protected static Application ourApplication;

        public static Application getApplication() {
          return ourApplication;
        }      
      }
    """)
    myFixture.addClass("""
      package com.intellij.openapi.application;

      import com.intellij.openapi.components.ComponentManager;

      public interface Application extends ComponentManager {}
    """)
  }
}
