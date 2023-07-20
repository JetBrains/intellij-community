// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.themes

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

abstract class UnregisteredNamedColorInspectionTestBase : LightJavaCodeInsightFixtureTestCase() {

  protected val knownNamedColor = "CompletionPopup.background"

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(UnregisteredNamedColorInspection())

    myFixture.addClass("""
      package com.intellij.ui;

      public final class JBColor {
        public static void namedColor(String s, int i) {}
      }
    """.trimIndent())
  }

}
