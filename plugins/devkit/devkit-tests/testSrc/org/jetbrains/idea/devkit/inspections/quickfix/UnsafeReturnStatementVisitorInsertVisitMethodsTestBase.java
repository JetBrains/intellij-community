// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.inspections.internal.UnsafeReturnStatementVisitorInspection;

abstract class UnsafeReturnStatementVisitorInsertVisitMethodsTestBase extends DevKitInspectionFixTestBase {

  protected static final String INSERT_LAMBDA_AND_CLASS_VISIT_METHODS = "Insert visitLambdaExpression/visitClass methods";
  protected static final String INSERT_LAMBDA_VISIT_METHOD = "Insert visitLambdaExpression method";
  protected static final String INSERT_CLASS_VISIT_METHOD = "Insert visitClass method";

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
}
