// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

abstract class ExtensionRegisteredAsServiceOrComponentInspectionTestBase : PluginModuleTestCase() {

  override fun setUp() {
    super.setUp()
    addClasses()
    myFixture.enableInspections(ExtensionRegisteredAsServiceOrComponentInspection::class.java)
  }

  private fun addClasses() {
    myFixture.addClass(
      //language=java
      """
        package com.intellij.openapi.extensions; 
        
        public class ExtensionPointName<T> { 
          public ExtensionPointName(String name) { }
        }
      """.trimIndent()
    )

    myFixture.addClass(
      //language=java
      """
        import com.intellij.openapi.extensions.ExtensionPointName;
        
        public interface MyExtension {
          ExtensionPointName<MyExtension> EP_NAME = new ExtensionPointName<>("com.intellij.example.myExtension");
        }
      """.trimIndent()
    )

    myFixture.addClass(
      //language=java
      """
        package com.intellij.openapi.components;
        
        public @interface Service { }
      """.trimIndent()
    )

    myFixture.addClass(
      //language=java
      """
        package com.intellij.openapi.components;
        
        public interface BaseComponent { }
      """.trimIndent()
    )

    myFixture.addClass(
      //language=java
      """
        package com.intellij.util.xmlb.annotations;
        
        public @interface Attribute { }
      """.trimIndent()
    )
  }

  protected open fun doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension())
  }

  protected abstract fun getFileExtension(): String
}
