// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public abstract class QuickFixGetFamilyNameViolationInspectionTestBase extends JavaCodeInsightFixtureTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(QuickFixGetFamilyNameViolationInspection.class);
    myFixture.addClass("""
                       package com.intellij.openapi.project;
                       public interface Project {}""");
    myFixture.addClass("""
                       package com.intellij.psi;
                       public interface PsiElement {}""");
    myFixture.addClass("""
                       package com.intellij.codeInspection;
                       public interface CommonProblemDescriptor {}""");
    myFixture.addClass("""
                       package com.intellij.codeInspection;
                       public interface ProblemDescriptor extends CommonProblemDescriptor {}""");
    myFixture.addClass("""
                       package com.intellij.codeInspection;
                       import com.intellij.openapi.project.Project;
                       public interface QuickFix<D extends CommonProblemDescriptor> {
                         String getName();
                         String getFamilyName();
                         void applyFix(Project project, D descriptor);
                       }""");
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }

  @NotNull
  protected abstract String getFileExtension();
}
