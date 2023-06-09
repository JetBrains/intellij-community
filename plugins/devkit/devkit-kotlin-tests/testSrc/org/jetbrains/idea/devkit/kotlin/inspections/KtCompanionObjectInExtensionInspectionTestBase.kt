// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

open class KtCompanionObjectInExtensionInspectionTestBase : LightJavaCodeInsightFixtureTestCase() {

  protected val fileExtension = "kt"

  override fun setUp() {
    super.setUp()
    myFixture.addClass(
      """
        package com.intellij.openapi.extensions; 
        
        public class ExtensionPointName<T> { 
          public ExtensionPointName(String name) { }
        }
      """.trimIndent()
    )

    myFixture.addClass(
      """
        import com.intellij.openapi.extensions.ExtensionPointName;
        
        public interface MyExtension {
          ExtensionPointName<MyExtension> EP_NAME = new ExtensionPointName<>("com.intellij.example.myExtension");
        }
      """.trimIndent()
    )

    myFixture.addClass(
      """
        package com.intellij.openapi.components;
        
        public @interface Service { }
      """.trimIndent()
    )

    myFixture.addClass(
      """
        package com.intellij.openapi.diagnostic;

        public class Logger { }
      """.trimIndent()
    )

    myFixture.addClass(
      """      
      public @interface MyAnnotation { }
      """.trimIndent()
    )

    myFixture.configureByFile("plugin.xml")
    myFixture.enableInspections(CompanionObjectInExtensionInspection())
  }
}
