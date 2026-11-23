// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.UseGrayInspection

/**
 * The fixture of the convert to gray quick fix tests. Create it with [convertToGrayFixture].
 *
 * The fixture enables [UseGrayInspection]. The project has the stubs of `Paint`, `Color`, `JBColor` and `Gray`.
 */
class ConvertToGrayFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  companion object {
    /**
     * The name pattern of the quick fix. The argument is the gray value.
     */
    const val CONVERT_TO_GRAY_FIX_NAME_PATTERN: String = "Convert to 'Gray._%d'"
  }
}

/**
 * Creates a [ConvertToGrayFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun convertToGrayFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<ConvertToGrayFixture> = testFixture("convertToGrayFixture") {
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
      public static final Gray _25 = null;
      public static final Gray _125 = null;
      private Gray(int num) {
        super(num, num, num);
      }
    }
    """.trimIndent())
  initialized(ConvertToGrayFixture(fixture, fileExtension)) {}
}
