// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixture
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture

/**
 * Creates the [LightDevKitInspectionFixture] of the [UseGrayInspection] tests for one test. Declare it as an instance field.
 *
 * The fixture enables [UseGrayInspection]. The project has the stubs of `Paint`, `Color`, `JBColor` and `Gray`.
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun useGrayInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<LightDevKitInspectionFixture> = testFixture("useGrayInspectionFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.enableInspections(UseGrayInspection())
  fixture.addClass("""
    package java.awt;
    public interface Paint {}
    """.trimIndent())
  fixture.addClass("""
    package java.awt;
    import java.io.Serializable;
    public class Color implements Paint, Serializable {
        public static final Color LIGHT_GRAY = null;
        public static final Color GRAY = null;
        public Color(int r, int g, int b) {}
        public Color(int r, int g, int b, int a) {}
        public Color(int rgb) {}
    }
    """.trimIndent())
  // adding it via 'PsiTestUtil.addLibrary()' causes issues in Kotlin tests (it can't link JBColor/Gray with Color added with addClass()
  fixture.addClass("""
    package com.intellij.ui;
    import java.awt.Color;
    public class JBColor extends Color {
        public JBColor(Color regular, Color dark) {}
    }
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.ui;
    import java.awt.Color;
    public final class Gray extends Color {
      private Gray(int num) {
        super(num, num, num);
      }
    }
    """.trimIndent())
  initialized(devKit) {}
}
