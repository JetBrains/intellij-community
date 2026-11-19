// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.quickfix.UnsafeReturnStatementVisitorFixture.INSERT_CLASS_VISIT_METHOD;
import static org.jetbrains.idea.devkit.inspections.quickfix.UnsafeReturnStatementVisitorFixture.INSERT_LAMBDA_AND_CLASS_VISIT_METHODS;
import static org.jetbrains.idea.devkit.inspections.quickfix.UnsafeReturnStatementVisitorFixture.INSERT_LAMBDA_VISIT_METHOD;
import static org.jetbrains.idea.devkit.inspections.quickfix.UnsafeReturnStatementVisitorFixtureKt.unsafeReturnStatementVisitorFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/unsafeReturnStatementVisitorFix")
@TestApplication
public class UnsafeReturnStatementVisitorInsertVisitMethodsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<UnsafeReturnStatementVisitorFixture> visitor =
    unsafeReturnStatementVisitorFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/unsafeReturnStatementVisitorFix");

  @Test
  public void testInsertVisitLambdaExpressionAndVisitClassMethods() {
    visitor.get().doTest(INSERT_LAMBDA_AND_CLASS_VISIT_METHODS);
  }

  @Test
  public void testInsertVisitLambdaExpressionMethod() {
    visitor.get().doTest(INSERT_LAMBDA_VISIT_METHOD);
  }

  @Test
  public void testInsertVisitClassMethod() {
    visitor.get().doTest(INSERT_CLASS_VISIT_METHOD);
  }
}
