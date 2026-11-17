// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * A named color key that the theme metadata registers.
 */
const val KNOWN_NAMED_COLOR: String = "CompletionPopup.background"

/**
 * Creates the code insight fixture of the [UnregisteredNamedColorInspection] tests for one test. Declare it as an instance field.
 *
 * The fixture enables [UnregisteredNamedColorInspection]. The project has the stub of `JBColor`.
 */
fun unregisteredNamedColorInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<LightJavaCodeInsightTestFixture> = testFixture("unregisteredNamedColorInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  fixture.enableInspections(UnregisteredNamedColorInspection())

  fixture.addClass("""
    package com.intellij.ui;

    public final class JBColor {
      public static void namedColor(String s, int i) {}
    }
  """.trimIndent())
  initialized(fixture) {}
}
