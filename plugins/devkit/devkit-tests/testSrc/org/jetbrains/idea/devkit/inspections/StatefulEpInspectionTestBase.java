// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

public abstract class StatefulEpInspectionTestBase extends PluginModuleTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("package com.intellij.openapi.project; public interface Project {}");
    myFixture.addClass("package com.intellij.psi; public interface PsiElement {}");
    myFixture.addClass("package com.intellij.psi; public interface PsiReference {}");
    myFixture.addClass("package com.intellij.codeInspection; public interface LocalQuickFix {}");
    myFixture.addClass("package com.intellij.codeInspection; public interface ProblemDescriptor {}");
    myFixture.addClass("package com.intellij.openapi.components; public interface ProjectComponent {}");
    myFixture.addClass("package com.intellij.openapi.util; public class Ref<T> {}");
    myFixture.enableInspections(new StatefulEpInspection());
  }

  protected void doTest() {
    myFixture.testHighlighting(getTestName(false) + '.' + getFileExtension());
  }

  protected abstract String getFileExtension();

}
