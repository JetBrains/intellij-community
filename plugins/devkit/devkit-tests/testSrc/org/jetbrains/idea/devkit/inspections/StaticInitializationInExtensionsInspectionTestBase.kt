// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

abstract class StaticInitializationInExtensionsInspectionTestBase : PluginModuleTestCase() {

  protected abstract fun getFileExtension(): String

  override fun setUp() {
    super.setUp()
    addPlatformClasses()
    setPluginXml("plugin.xml")
    myFixture.enableInspections(StaticInitializationInExtensionsInspection())
  }

  private fun addPlatformClasses() {
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
        
        public interface MyExtension { }
      """.trimIndent()
    )
  }

  protected open fun doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension())
  }

}