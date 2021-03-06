/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.junit.codeInsight.references;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;

public class MethodSourceReference extends BaseJunitAnnotationReference {
  public MethodSourceReference(PsiLanguageInjectionHost element) {
    super(element);
  }

  @Override
  protected boolean hasNoStaticProblem(@NotNull PsiMethod method, @NotNull UClass literalClazz, @Nullable UMethod literalMethod) {
    boolean isStatic = method.hasModifierProperty(PsiModifier.STATIC);
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return false;
    return (TestUtils.testInstancePerClass(psiClass) != isStatic) && method.getParameterList().isEmpty();
  }
}
