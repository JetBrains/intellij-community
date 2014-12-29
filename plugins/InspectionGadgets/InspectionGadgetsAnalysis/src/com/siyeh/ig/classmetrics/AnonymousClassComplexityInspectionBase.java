/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.classmetrics;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class AnonymousClassComplexityInspectionBase extends ClassMetricInspection {
  private static final int DEFAULT_COMPLEXITY_LIMIT = 3;

  @Override
  @NotNull
  public String getID() {
    return "OverlyComplexAnonymousInnerClass";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "overly.complex.anonymous.inner.class.display.name");
  }

  @Override
  protected int getDefaultLimit() {
    return DEFAULT_COMPLEXITY_LIMIT;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message(
      "cyclomatic.complexity.limit.option");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer totalComplexity = (Integer)infos[0];
    return InspectionGadgetsBundle.message(
      "overly.complex.anonymous.inner.class.problem.descriptor",
      totalComplexity);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassComplexityVisitor();
  }

  private class ClassComplexityVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAnonymousClass(
      @NotNull PsiAnonymousClass aClass) {
      if (aClass instanceof PsiEnumConstantInitializer) {
        return;
      }
      final int totalComplexity = calculateTotalComplexity(aClass);
      if (totalComplexity <= getLimit()) {
        return;
      }
      registerClassError(aClass, Integer.valueOf(totalComplexity));
    }

    private int calculateTotalComplexity(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      int totalComplexity = calculateComplexityForMethods(methods);
      totalComplexity += calculateInitializerComplexity(aClass);
      return totalComplexity;
    }

    private int calculateInitializerComplexity(PsiClass aClass) {
      final CyclomaticComplexityVisitor visitor = new CyclomaticComplexityVisitor();
      int complexity = 0;
      final PsiClassInitializer[] initializers = aClass.getInitializers();
      for (final PsiClassInitializer initializer : initializers) {
        visitor.reset();
        initializer.accept(visitor);
        complexity += visitor.getComplexity();
      }
      return complexity;
    }

    private int calculateComplexityForMethods(PsiMethod[] methods) {
      final CyclomaticComplexityVisitor visitor = new CyclomaticComplexityVisitor();
      int complexity = 0;
      for (final PsiMethod method : methods) {
        visitor.reset();
        method.accept(visitor);
        complexity += visitor.getComplexity();
      }
      return complexity;
    }
  }
}
