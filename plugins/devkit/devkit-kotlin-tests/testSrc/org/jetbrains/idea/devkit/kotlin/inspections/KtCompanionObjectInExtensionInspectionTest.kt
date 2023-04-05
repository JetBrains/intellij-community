// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.PluginModuleTestCase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("/inspections/companionObjectInExtension")
class KtCompanionObjectInExtensionInspectionTest : PluginModuleTestCase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/companionObjectInExtension"

  override fun setUp() {
    super.setUp()
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
        package com.intellij.openapi.diagnostic;

        public class Logger { }
      """.trimIndent()
    )

    myFixture.enableInspections(CompanionObjectInExtensionInspection::class.java)
  }

  fun testNoHighlighting() {
    setPluginXml("plugin.xml")
    myFixture.testHighlighting("ClassWithCompanionObject.kt")
  }

  fun testExtensionWithCompanionObjects() {
    setPluginXml("plugin.xml")
    myFixture.testHighlighting("ExtensionWithCompanionObject.kt")
  }

  fun testExtensionWithLoggerAndConstVal() {
    setPluginXml("plugin.xml")
    myFixture.testHighlighting("ExtensionWithLoggerAndConstVal.kt")
  }

}