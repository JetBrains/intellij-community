// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.idea.devkit.DevKitBundle

/**
 * The fixture of the [UseDPIAwareEmptyBorderInspection] tests. Create it with [useDPIAwareEmptyBorderFixture].
 *
 * The project has the stubs of `EmptyBorder`, `Insets`, `JBEmptyBorder` and `JBUI.Borders`.
 */
class UseDPIAwareEmptyBorderFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val fileExtension: String,
) {
  /**
   * Checks the highlighting of the test data file `<test name>.<file extension>`.
   */
  fun doTest() {
    fixture.testHighlighting(fixture.getTestName(false) + '.' + fileExtension)
  }

  /**
   * Checks the highlighting of [before] in `TestClass.<file extension>`, applies the quick fix [fixName] and checks [after].
   * Call it from any thread. It applies the quick fix on the EDT.
   */
  fun doTest(fixName: String, before: String, after: String) {
    fixture.configureByText("TestClass.$fileExtension", before)
    fixture.checkHighlighting()
    runInEdtAndWait {
      val intention = fixture.findSingleIntention(fixName)
      fixture.checkPreviewAndLaunchAction(intention)
      fixture.checkResult(after, true)
    }
  }

  companion object {
    @JvmField
    val SIMPLIFY_FIX_NAME: String = DevKitBundle.message("inspections.use.dpi.aware.empty.border.simplify.fix.name")

    @JvmField
    val CONVERT_TO_JB_UI_BORDERS_EMPTY_FIX_NAME: String = DevKitBundle.message("inspections.use.dpi.aware.empty.border.convert.fix.name")
  }
}

/**
 * Creates a [UseDPIAwareEmptyBorderFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun useDPIAwareEmptyBorderFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<UseDPIAwareEmptyBorderFixture> = testFixture("useDPIAwareEmptyBorderFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  fixture.enableInspections(UseDPIAwareEmptyBorderInspection())
  fixture.addClass("""
                     package javax.swing.border;
                     import java.awt.Insets;
                     public class EmptyBorder implements Border {
                       public EmptyBorder(int top, int left, int bottom, int right) {}
                       public EmptyBorder(Insets borderInsets) {}
                     }
                     """.trimIndent())
  fixture.addClass("""
                     package com.intellij.util.ui;
                     import javax.swing.border.EmptyBorder;
                     public class JBEmptyBorder extends EmptyBorder {}
                     """.trimIndent())
  fixture.addClass("""
                     package java.awt;
                     public class Insets {
                         public Insets(int top, int left, int bottom, int right) {}
                     }
                     """.trimIndent())
  fixture.addClass("""
                     package javax.swing.border;
                     public interface Border {}
                     """.trimIndent())
  // adding it via 'PsiTestUtil.addLibrary()' causes issues in Kotlin tests (it can't link JBUI with Border added with addClass()
  fixture.addClass("""
                     package com.intellij.util.ui;
                     import java.awt.Insets;
                     import javax.swing.border.Border;
                     public final class JBUI {
                       public static final class Borders {
                         public static JBEmptyBorder empty() {return null;}
                         public static Border empty(int offsets) {return null;}
                         public static JBEmptyBorder empty(int topAndBottom, int leftAndRight) {return null;}
                         public static JBEmptyBorder empty(int top, int left, int bottom, int right) {return null;}
                         public static Border empty(Insets insets) {return null;}
                       }
                     }
                     """.trimIndent())
  initialized(UseDPIAwareEmptyBorderFixture(fixture, fileExtension)) {}
}
