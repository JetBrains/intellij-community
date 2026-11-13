// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * The fixture of the [QuickFixGetFamilyNameViolationInspection] tests. Create it with [quickFixGetFamilyNameViolationInspectionFixture].
 *
 * The project has the stubs of `Project`, `PsiElement`, `ProblemDescriptor` and `QuickFix`.
 */
class QuickFixGetFamilyNameViolationInspectionFixture internal constructor(
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
}

/**
 * Creates a [QuickFixGetFamilyNameViolationInspectionFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun quickFixGetFamilyNameViolationInspectionFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<QuickFixGetFamilyNameViolationInspectionFixture> = testFixture("quickFixGetFamilyNameViolationInspectionFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture, basePath).init()
  fixture.enableInspections(QuickFixGetFamilyNameViolationInspection::class.java)
  fixture.addClass("""
                   package com.intellij.openapi.project;
                   public interface Project {}""".trimIndent())
  fixture.addClass("""
                   package com.intellij.psi;
                   public interface PsiElement {}""".trimIndent())
  fixture.addClass("""
                   package com.intellij.codeInspection;
                   public interface CommonProblemDescriptor {}""".trimIndent())
  fixture.addClass("""
                   package com.intellij.codeInspection;
                   public interface ProblemDescriptor extends CommonProblemDescriptor {}""".trimIndent())
  fixture.addClass("""
                   package com.intellij.codeInspection;
                   import com.intellij.openapi.project.Project;
                   public interface QuickFix<D extends CommonProblemDescriptor> {
                     String getName();
                     String getFamilyName();
                     void applyFix(Project project, D descriptor);
                   }""".trimIndent())
  initialized(QuickFixGetFamilyNameViolationInspectionFixture(fixture, fileExtension)) {}
}
