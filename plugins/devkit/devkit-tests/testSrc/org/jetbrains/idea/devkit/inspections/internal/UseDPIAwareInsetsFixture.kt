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
 * The fixture of the [UseDPIAwareInsetsInspection] tests. Create it with [useDPIAwareInsetsFixture].
 *
 * The project has the stubs of `Insets`, `JBInsets` and `JBUI`.
 */
class UseDPIAwareInsetsFixture internal constructor(
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
    val SIMPLIFY_FIX_NAME: String = DevKitBundle.message("inspections.use.dpi.aware.insets.simplify.fix.name")

    @JvmField
    val CONVERT_TO_JB_UI_INSETS_EMPTY_FIX_NAME: String = DevKitBundle.message("inspections.use.dpi.aware.insets.convert.fix.name")
  }
}

/**
 * Creates a [UseDPIAwareInsetsFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun useDPIAwareInsetsFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<UseDPIAwareInsetsFixture> = testFixture("useDPIAwareInsetsFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  fixture.enableInspections(UseDPIAwareInsetsInspection())
  fixture.addClass("""
                     package java.awt;
                     public class Insets {
                       public Insets(int top, int left, int bottom, int right) {}
                     }
                     """.trimIndent())
  // adding it via 'PsiTestUtil.addLibrary()' causes issues in Kotlin tests (it can't link JBInsets/JBUI with Border added with addClass()
  fixture.addClass("""
                     package com.intellij.util.ui;
                     import java.awt.Insets;
                     public class JBInsets extends Insets {
                       public static JBInsets create(Insets insets) {return null;}
                     }
                     """.trimIndent())
  fixture.addClass("""
                     package com.intellij.util.ui;
                     import java.awt.Insets;
                     public final class JBUI {
                       public static JBInsets insets(int top, int left, int bottom, int right) {return null;}
                       public static JBInsets insets(int all) {return null;}
                       public static JBInsets insets(String propName, JBInsets defaultValue) {return null;}
                       public static JBInsets insets(int topBottom, int leftRight) {return null;}
                       public static JBInsets emptyInsets() {return null;}
                       public static JBInsets insetsTop(int t) {return null;}
                       public static JBInsets insetsLeft(int l) {return null;}
                       public static JBInsets insetsBottom(int b) {return null;}
                       public static JBInsets insetsRight(int r) {return new JBInsets(0, 0, 0, r);}
                       public static JBInsets insets(Insets insets) {return null;}
                     }
                     """.trimIndent())
  initialized(UseDPIAwareInsetsFixture(fixture, fileExtension)) {}
}
