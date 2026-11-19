// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.idea.devkit.kotlin.inspections.PrivateExtensionClassInspection
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

/**
 * Creates the project of a [PrivateExtensionClassInspection] test class: the JDK and the Kotlin standard library.
 * Declare it in a companion object.
 */
fun privateExtensionClassProjectFixture(): TestFixture<LightJavaProject> =
  lightJavaProjectFixture(KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance())

/**
 * Creates a [LightDevKitInspectionFixture] of the [PrivateExtensionClassInspection] tests for one test. Declare it as an instance field.
 * Give it the project of [privateExtensionClassProjectFixture].
 *
 * The test data files are Kotlin files. The fixture enables the inspection and configures `plugin.xml` of the test data.
 * The project has the stubs of the extension, action and service API.
 */
fun privateExtensionClassFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<LightDevKitInspectionFixture> =
  testFixture("privateExtensionClassFixture") {
    val devKit = lightDevKitInspectionFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/privateExtension").init()
    val fixture = devKit.fixture
    fixture.addClass(
      """
        package com.intellij.openapi.extensions; 
      
        public class ExtensionPointName<T> { 
          public ExtensionPointName(String name) { }
        }
      """.trimIndent()
    )
    fixture.addClass(
      """
        import com.intellij.openapi.extensions.ExtensionPointName;
      
        public interface MyExtension {
          ExtensionPointName<MyExtension> EP_NAME = new ExtensionPointName<>("com.intellij.example.myExtension");
        }
      """.trimIndent()
    )
    fixture.addClass(
      """
      package com.intellij.openapi.actionSystem;
    
      public class AnAction { }
    """.trimIndent())
    fixture.addClass("""
      package com.intellij.util.xmlb.annotations;
    
      public @interface Attribute { String value() default ""; }
    """.trimIndent())
    fixture.addClass("""
      package com.intellij.openapi.components;
    
      import com.intellij.util.xmlb.annotations.Attribute;
    
      public class ServiceDescriptor {
        @Attribute("serviceImplementation")
        public String serviceImplementation;
    
        @Attribute("serviceInterface")
        public String serviceInterface;
      }
    """.trimIndent())
    fixture.configureByFile("plugin.xml")
    fixture.enableInspections(PrivateExtensionClassInspection())
    initialized(devKit) {}
  }