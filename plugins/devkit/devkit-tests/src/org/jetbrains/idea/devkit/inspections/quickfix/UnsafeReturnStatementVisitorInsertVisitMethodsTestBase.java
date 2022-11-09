// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.internal.UnsafeReturnStatementVisitorInspection;

abstract class UnsafeReturnStatementVisitorInsertVisitMethodsTestBase extends JavaCodeInsightFixtureTestCase {

  protected static final String INSERT_LAMBDA_AND_CLASS_VISIT_METHODS = "Insert visitLambdaExpression/visitClass methods";
  protected static final String INSERT_LAMBDA_VISIT_METHOD = "Insert visitLambdaExpression method";
  protected static final String INSERT_CLASS_VISIT_METHOD = "Insert visitClass method";

  @NotNull
  protected abstract String getFileExtension();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnsafeReturnStatementVisitorInspection());
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.addLibrary("platform-core-api", PathUtil.getJarPathForClass(PsiElement.class));
    moduleBuilder.addLibrary("java-psi", PathUtil.getJarPathForClass(JavaRecursiveElementWalkingVisitor.class));
  }

  protected void doTest(String fixName) {
    String testName = getTestName(false);
    String fileNameBefore = testName + '.' + getFileExtension();
    String fileNameAfter = testName + "_after." + getFileExtension();
    myFixture.testHighlighting(fileNameBefore);
    IntentionAction intention = myFixture.findSingleIntention(fixName);
    myFixture.checkPreviewAndLaunchAction(intention);
    myFixture.checkResultByFile(fileNameBefore, fileNameAfter, true);
  }

}
