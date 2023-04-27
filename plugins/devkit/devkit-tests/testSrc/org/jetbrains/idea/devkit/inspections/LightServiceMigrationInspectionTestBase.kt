// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

abstract class LightServiceMigrationInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()
    //language=java
    myFixture.addClass("""
      package com.intellij.openapi.components;
      public interface PersistentStateComponent<T> { }
    """)
    myFixture.addClass("""
      //language=java
      package com.intellij.util.xmlb.annotations;
      
      public @interface Attribute { String value() default ""; }
    """)
    myFixture.addClass(
      //language=java
      """
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
    myFixture.addClass(
      //language=java
      """
        package com.intellij.openapi.components;
        public @interface Service {}
      """
    )
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
}