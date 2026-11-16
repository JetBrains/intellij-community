// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * The fixture of the [UseJBColorInspection] tests. Create it with [useJBColorFixture].
 *
 * The fixture enables [UseJBColorInspection]. The project has the stubs of `Paint`, `Color` and `JBColor`.
 */
class UseJBColorFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  companion object {
    /**
     * The name of the quick fix that converts a `Color` constructor call.
     */
    const val CONVERT_TO_JB_COLOR_FIX_NAME: String = "Convert to 'JBColor'"

    /**
     * The name of the quick fix that converts a `Color` constant.
     */
    const val CONVERT_TO_JB_COLOR_CONSTANT_FIX_NAME: String = "Convert to 'JBColor' constant"
  }
}

/**
 * Creates a [UseJBColorFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun useJBColorFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<UseJBColorFixture> = testFixture("useJBColorFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(UseJBColorInspection())
  fixture.addClass("""
    package java.awt;
    public interface Paint {}
    """.trimIndent())
  fixture.addClass("""
    package java.awt;
    import java.io.Serializable;
    public class Color implements Paint, Serializable {
        public static final Color white = null;
        public static final Color WHITE = null;
        public static final Color lightGray = null;
        public static final Color LIGHT_GRAY = null;
        public static final Color darkGray = null;
        public static final Color DARK_GRAY = null;
        public static final Color black = null;
        public static final Color BLACK = null;
        public static final Color red = null;
        public static final Color RED = null;
        public static final Color yellow = null;
        public static final Color YELLOW = null;
        public static final Color green = null;
        public static final Color GREEN = null;
        public static final Color blue = null;
        public static final Color BLUE = null;
        public Color(int r, int g, int b) {}
    }
    """.trimIndent())
  // adding it via 'PsiTestUtil.addLibrary()' causes issues in Kotlin tests (it can't link JBColor with Color added with addClass()
  fixture.addClass("""
    package com.intellij.ui;
    import java.awt.Color;
    public class JBColor extends Color {
        public static final JBColor white = null;
        public static final JBColor WHITE = null;
        public static final JBColor lightGray = null;
        public static final JBColor LIGHT_GRAY = null;
        public static final JBColor darkGray = null;
        public static final JBColor DARK_GRAY = null;
        public static final JBColor black = null;
        public static final JBColor BLACK = null;
        public static final JBColor red = null;
        public static final JBColor RED = null;
        public static final JBColor yellow = null;
        public static final JBColor YELLOW = null;
        public static final JBColor green = null;
        public static final JBColor GREEN = null;
        public static final JBColor blue = null;
        public static final JBColor BLUE = null;
        public JBColor(Color regular, Color dark) {}
    }
    """.trimIndent())
  initialized(UseJBColorFixture(fixture, fileExtension)) {}
}
