// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.lang.properties.codeInspection.unused.UnusedPropertyInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class JUnitPlatformPropertiesTest : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()

    // define property source class
    myFixture.addClass("""
      package org.junit.jupiter.engine;

      public final class Constants {
          public static final String DEACTIVATE_CONDITIONS_PATTERN_PROPERTY_NAME = "junit.jupiter.conditions.deactivate";
          public static final String DEACTIVATE_ALL_CONDITIONS_PATTERN = "*";
          public static final String DEFAULT_DISPLAY_NAME_GENERATOR_PROPERTY_NAME = "junit.jupiter.displayname.generator.default";
          public static final String EXTENSIONS_AUTODETECTION_ENABLED_PROPERTY_NAME = "junit.jupiter.extensions.autodetection.enabled";
          public static final String DEFAULT_TEST_INSTANCE_LIFECYCLE_PROPERTY_NAME = "junit.jupiter.testinstance.lifecycle.default";
          public static final String PARALLEL_EXECUTION_ENABLED_PROPERTY_NAME = "junit.jupiter.execution.parallel.enabled";
          public static final String DEFAULT_PARALLEL_EXECUTION_MODE = "junit.jupiter.execution.parallel.mode.default";
      }
    """.trimIndent())
  }

  fun testImplicitUsage() {
    myFixture.enableInspections(UnusedPropertyInspection::class.java)

    myFixture.configureByText("junit-platform.properties", """
      junit.jupiter.displayname.generator.default = {index}
      <warning descr="Unused property">junit.unknown</warning> = unknown
    """.trimIndent())

    myFixture.checkHighlighting()
  }

  fun testCompletion() {
    myFixture.configureByText("junit-platform.properties", """
       junit.jupiter.<caret>
    """.trimIndent())

    myFixture.testCompletionVariants("junit-platform.properties",
                                     "junit.jupiter.conditions.deactivate",
                                     "junit.jupiter.displayname.generator.default",
                                     "junit.jupiter.execution.parallel.enabled",
                                     "junit.jupiter.extensions.autodetection.enabled",
                                     "junit.jupiter.testinstance.lifecycle.default",
                                     "junit.jupiter.execution.parallel.mode.default")
  }
}