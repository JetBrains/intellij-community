// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit

import com.intellij.lang.properties.codeInspection.unused.UnusedPropertyInspection
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.psi.util.PsiUtilCore
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

  fun testMetadataKeyCompletion() {
    addMetadata()

    myFixture.configureByText("junit-platform.properties", "junit.custom.<caret>")
    myFixture.completeBasic()

    assertContainsElements(myFixture.lookupElementStrings!!, "junit.custom.enabled", "junit.custom.mode")
  }

  fun testMetadataValueCompletion() {
    addMetadata()

    myFixture.configureByText("junit-platform.properties", "junit.custom.enabled=<caret>")
    myFixture.completeBasic()

    assertContainsElements(myFixture.lookupElementStrings!!, "true", "false")
  }

  fun testMetadataDocumentation() {
    addMetadata()

    // The plain-text description is escaped before it is shown as documentation HTML.
    assertEquals("Enables the &lt;b&gt;custom&lt;/b&gt; feature.", documentationForKey("junit.custom.enabled=true"))
  }

  fun testMetadataMergesWithConstants() {
    // A key known from `Constants` that is also described by metadata keeps navigation but gains docs and value hints.
    addMetadata()

    myFixture.configureByText("junit-platform.properties", "junit.jupiter.execution.parallel.enabled=<caret>")
    myFixture.completeBasic()
    assertContainsElements(myFixture.lookupElementStrings!!, "true", "false")

    assertEquals("Runs tests in parallel.", documentationForKey("junit.jupiter.execution.parallel.enabled=true"))
  }

  private fun documentationForKey(propertiesLine: String): String? {
    myFixture.configureByText("junit-platform.properties", propertiesLine)
    val key = PsiUtilCore.getElementAtOffset(myFixture.file, 0)
    val target = PsiDocumentationTargetProvider.EP_NAME.computeSafeIfAny { it.documentationTarget(key, key) } ?: return null
    return computeDocumentationBlocking(target.createPointer())?.html
  }

  private fun addMetadata() {
    myFixture.addFileToProject("META-INF/junit-platform-configuration-metadata.json", """
      {
        "properties": [
          { "name": "junit.custom.enabled", "description": "Enables the <b>custom</b> feature." },
          { "name": "junit.custom.mode" },
          { "name": "junit.jupiter.execution.parallel.enabled", "description": "Runs tests in parallel." }
        ],
        "hints": [
          { "name": "junit.custom.enabled", "values": [ { "value": true }, { "value": false } ] },
          { "name": "junit.jupiter.execution.parallel.enabled", "values": [ { "value": "true" }, { "value": "false" } ] }
        ]
      }
    """.trimIndent())
  }
}