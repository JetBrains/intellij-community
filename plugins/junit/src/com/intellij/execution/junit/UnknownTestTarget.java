// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.execution.JUnitBundle;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;

public class UnknownTestTarget extends TestObject {
  protected UnknownTestTarget(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  public String suggestActionName() {
    return JUnitBundle.message("action.text.test.unknown.target");
  }

  @Override
  public RefactoringElementListener getListener(PsiElement element) {
    return null;
  }

  @Override
  public boolean isConfiguredByElement(JUnitConfiguration configuration,
                                       PsiClass testClass,
                                       PsiMethod testMethod,
                                       PsiPackage testPackage,
                                       PsiDirectory testDir) {
    return false;
  }
}
