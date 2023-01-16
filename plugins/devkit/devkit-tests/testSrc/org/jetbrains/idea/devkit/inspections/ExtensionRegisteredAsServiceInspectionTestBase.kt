// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

abstract class ExtensionRegisteredAsServiceInspectionTestBase : PluginModuleTestCase() {

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(ExtensionRegisteredAsServiceInspection::class.java)
  }

  protected fun setUpExtensionTesting() {
    myFixture.addClass(
      //language=java
      """
      package com.intellij.openapi.extensions; 
      
      public class ExtensionPointName<T> {
        public ExtensionPointName(String name) { }
      }"""
    )

    myFixture.addClass(
      //language=java
      """
        import com.intellij.openapi.extensions.ExtensionPointName;

        public interface MyExtension {
          ExtensionPointName<MyExtension> EP_NAME = new ExtensionPointName<>("com.intellij.example.myExtension");
        }
      """
    )
  }

  protected fun setUpLightServiceTesting() {
    myFixture.addClass(
      //language=java
      """
        package com.intellij.openapi.components;
        
        public @interface Service { }
    """
    )
  }

  abstract fun testExtensionNoHighlighting()

  abstract fun testServiceNoHighlighting()

  abstract fun testLightServiceExtension()

  abstract fun testServiceExtension()

}
