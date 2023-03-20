// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.inspections.internal.UnsafeReturnStatementVisitorInspectionTestBase;
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/unsafeReturnStatementVisitor")
public class KtUnsafeReturnStatementVisitorInspectionTest extends UnsafeReturnStatementVisitorInspectionTestBase {

  @Override
  protected String getBasePath() {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/unsafeReturnStatementVisitor";
  }

  @Override
  protected @NotNull String getFileExtension() {
    return "kt";
  }

  public void testUnsafeVisitReturnStatementUsedInJavaRecursiveElementVisitor() {
    doTest();
  }

  public void testUnsafeVisitReturnStatementUsedInJavaRecursiveElementWalkingVisitor() {
    doTest();
  }

  public void testNoUnsafeVisitReturnStatements() {
    doTest();
  }
}
