/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.junit;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.psi.*;
import com.intellij.refactoring.listeners.RefactoringElementListener;

public class UnknownTestTarget extends TestObject {
  protected UnknownTestTarget(JUnitConfiguration configuration, ExecutionEnvironment environment) {
    super(configuration, environment);
  }

  @Override
  public String suggestActionName() {
    return "Unknown";
  }

  @Override
  public RefactoringElementListener getListener(PsiElement element, JUnitConfiguration configuration) {
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
