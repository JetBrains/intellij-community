// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.openapi.util.Iconable;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;

public abstract class UnsafeReturnStatementVisitorInspectionTestBase extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnsafeReturnStatementVisitorInspection());
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.addLibrary("platform-core-api", PathUtil.getJarPathForClass(PsiElement.class));
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(Iconable.class));
    moduleBuilder.addLibrary("java-psi", PathUtil.getJarPathForClass(JavaRecursiveElementWalkingVisitor.class));
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }

  @NotNull
  protected abstract String getFileExtension();
}
