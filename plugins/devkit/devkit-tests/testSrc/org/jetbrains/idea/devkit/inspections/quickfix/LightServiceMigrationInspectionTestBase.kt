// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import org.jetbrains.idea.devkit.inspections.LightServiceMigrationCodeInspection
import org.jetbrains.idea.devkit.inspections.LightServiceMigrationXMLInspection

abstract class LightServiceMigrationInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    addClasses()
    myFixture.enableInspections(LightServiceMigrationXMLInspection::class.java,
                                LightServiceMigrationCodeInspection::class.java)
  }

  protected fun doTest(codeFilePath: String, xmlFilePath: String) {
    myFixture.testHighlightingAllFiles(true, false, false, codeFilePath, xmlFilePath)
  }

  protected fun getCodeFilePath(): String {
    return getTestName(false) + "." + fileExtension
  }

  protected fun getXmlFilePath(): String {
    return getTestName(true) + ".xml"
  }

  private fun addClasses() {
    myFixture.addClass("""
      package com.intellij.openapi.components;
      public interface PersistentStateComponent<T> { }
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

        @Attribute("preload")
        public PreloadMode preload;

        public enum PreloadMode {}
      }
    """)
    myFixture.addClass("""
        package com.intellij.openapi.components;
        public @interface Service {}
      """
    )
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

      public interface Application {
        boolean isUnitTestMode();
        boolean isHeadlessEnvironment();
      }
    """)
  }
}