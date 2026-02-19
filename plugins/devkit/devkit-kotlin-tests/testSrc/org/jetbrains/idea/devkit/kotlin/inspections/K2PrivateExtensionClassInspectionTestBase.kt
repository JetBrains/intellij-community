// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class K2PrivateExtensionClassInspectionTestBase : LightDevKitInspectionFixTestBase(), ExpectedPluginModeProvider {
  override fun getFileExtension(): String = "kt"

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/privateExtension"

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
  }

  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
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
      package com.intellij.openapi.actionSystem;
      
      public class AnAction { }
    """.trimIndent())

    myFixture.addClass("""
      package com.intellij.util.xmlb.annotations;
      
      public @interface Attribute { String value() default ""; }
    """.trimIndent())
    myFixture.addClass("""
      package com.intellij.openapi.components;
      
      import com.intellij.util.xmlb.annotations.Attribute;
      
      public class ServiceDescriptor {
        @Attribute("serviceImplementation")
        public String serviceImplementation;
      
        @Attribute("serviceInterface")
        public String serviceInterface;
      }
    """.trimIndent())

    myFixture.configureByFile("plugin.xml")
    myFixture.enableInspections(PrivateExtensionClassInspection())
  }
}
