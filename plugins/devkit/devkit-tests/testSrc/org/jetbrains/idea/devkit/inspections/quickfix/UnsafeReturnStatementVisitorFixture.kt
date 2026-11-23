// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.idea.devkit.inspections.internal.UnsafeReturnStatementVisitorInspection

/**
 * The fixture of the [UnsafeReturnStatementVisitorInspection] tests. Create it with [unsafeReturnStatementVisitorFixture].
 *
 * The fixture enables [UnsafeReturnStatementVisitorInspection]. The project has the stubs of the PSI visitors and elements.
 */
class UnsafeReturnStatementVisitorFixture(fixture: LightJavaCodeInsightTestFixture, fileExtension: String) :
  LightDevKitInspectionFixture(fixture, fileExtension) {
  companion object {
    /**
     * The name of the quick fix that inserts both visit methods.
     */
    const val INSERT_LAMBDA_AND_CLASS_VISIT_METHODS: String = "Insert visitLambdaExpression/visitClass methods"

    /**
     * The name of the quick fix that inserts `visitLambdaExpression`.
     */
    const val INSERT_LAMBDA_VISIT_METHOD: String = "Insert visitLambdaExpression method"

    /**
     * The name of the quick fix that inserts `visitClass`.
     */
    const val INSERT_CLASS_VISIT_METHOD: String = "Insert visitClass method"
  }
}

/**
 * Creates a [UnsafeReturnStatementVisitorFixture] for one test. Declare it as an instance field.
 *
 * [fileExtension] is the extension of the test data files. [basePath] is the test data path under the community directory.
 */
@JvmOverloads
fun unsafeReturnStatementVisitorFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileExtension: String,
  basePath: String = "",
): TestFixture<UnsafeReturnStatementVisitorFixture> = testFixture("unsafeReturnStatementVisitorFixture") {
  val devKit = lightDevKitInspectionFixture(projectFixture, fileExtension, basePath).init()
  val fixture = devKit.fixture
  fixture.addClass("""
    package com.intellij.psi;
    public abstract class PsiElementVisitor {}
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    public interface PsiElement {
      void accept(PsiElementVisitor visitor);
    }
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    public interface PsiClass extends PsiElement {}
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    public interface PsiReturnStatement extends PsiElement {}
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    public interface PsiLambdaExpression extends PsiElement {}
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    public abstract class JavaElementVisitor extends PsiElementVisitor {
      public void visitClass(PsiClass aClass) {}
      public void visitReturnStatement(PsiReturnStatement statement) {}
      public void visitLambdaExpression(PsiLambdaExpression expression) {}
    }
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    public abstract class JavaRecursiveElementVisitor extends JavaElementVisitor {}
    """.trimIndent())
  fixture.addClass("""
    package com.intellij.psi;
    public abstract class JavaRecursiveElementWalkingVisitor extends JavaElementVisitor {}
    """.trimIndent())
  fixture.enableInspections(UnsafeReturnStatementVisitorInspection())
  initialized(UnsafeReturnStatementVisitorFixture(fixture, fileExtension)) {}
}
