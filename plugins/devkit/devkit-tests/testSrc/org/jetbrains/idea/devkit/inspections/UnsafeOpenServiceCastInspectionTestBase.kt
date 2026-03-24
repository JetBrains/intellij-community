// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

abstract class UnsafeOpenServiceCastInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UnsafeOpenServiceCastInspection())
    addClasses()
  }

  protected fun doTest(codeFilePath: String, xmlFilePath: String) {
    myFixture.testHighlightingAllFiles(true, false, false, codeFilePath, xmlFilePath)
  }

  private fun addClasses() {
    myFixture.addClass("""
      package com.intellij.openapi.components;

      public interface ComponentManager {
        <T> T getService(@org.jetbrains.annotations.NotNull Class<T> serviceClass);
        <T> T getServiceIfCreated(@org.jetbrains.annotations.NotNull Class<T> serviceClass);
      }
    """)
    myFixture.addClass("""
      package com.intellij.openapi.project;

      import com.intellij.openapi.components.ComponentManager;

      public interface Project extends ComponentManager {}
    """)
    myFixture.addClass("""
      package com.intellij.openapi.application;

      public final class ApplicationManager {
        private static Application ourApplication;

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
    myFixture.addClass("""
      package com.intellij.util.xmlb.annotations;

      public @interface Attribute { String value() default ""; }
    """)
    myFixture.addClass("""
      package com.intellij.openapi.components;

      import com.intellij.util.xmlb.annotations.Attribute;

      public class ServiceDescriptor {
        @Attribute("serviceImplementation")
        public String serviceImplementation;

        @Attribute("serviceInterface")
        public String serviceInterface;

        @Attribute("open")
        public boolean open;

        @Attribute("overrides")
        public boolean overrides;
      }
    """)
    myFixture.addClass("""
      package com.intellij.openapi.components;

      public @interface Service {
        Level[] value() default Level.APP;

        enum Level { APP, PROJECT }
      }
    """)
    myFixture.addClass("""
      package kotlin.reflect;

      public class KClass<T> {
        public Class<T> java;
      }
    """)
  }
}
