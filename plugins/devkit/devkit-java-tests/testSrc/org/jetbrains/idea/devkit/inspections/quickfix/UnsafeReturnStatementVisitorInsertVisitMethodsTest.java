// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/unsafeReturnStatementVisitorFix")
public class UnsafeReturnStatementVisitorInsertVisitMethodsTest extends UnsafeReturnStatementVisitorInsertVisitMethodsTestBase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/unsafeReturnStatementVisitorFix";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "java";
  }

  public void testInsertVisitLambdaExpressionAndVisitClassMethods() {
    doTest(INSERT_LAMBDA_AND_CLASS_VISIT_METHODS);
  }

  public void testInsertVisitLambdaExpressionMethod() {
    doTest(INSERT_LAMBDA_VISIT_METHOD);
  }

  public void testInsertVisitClassMethod() {
    doTest(INSERT_CLASS_VISIT_METHOD);
  }
}
