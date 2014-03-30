/*
 * Copyright 2006-2010 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

public class ComparableImplementedButEqualsNotOverriddenInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "comparable.implemented.but.equals.not.overridden.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "comparable.implemented.but.equals.not.overridden.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CompareToAndEqualsNotPairedVisitor();
  }

  private static class CompareToAndEqualsNotPairedVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      super.visitClass(aClass);
      final PsiMethod[] methods = aClass.findMethodsByName(
        HardcodedMethodConstants.COMPARE_TO, false);
      if (methods.length == 0) {
        return;
      }
      final Project project = aClass.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final GlobalSearchScope scope = aClass.getResolveScope();
      final PsiClass comparableClass =
        psiFacade.findClass(CommonClassNames.JAVA_LANG_COMPARABLE,
                            scope);
      if (comparableClass == null) {
        return;
      }
      if (!aClass.isInheritor(comparableClass, true)) {
        return;
      }
      final PsiMethod compareToMethod = comparableClass.getMethods()[0];
      boolean foundCompareTo = false;
      for (PsiMethod method : methods) {
        if (MethodSignatureUtil.isSuperMethod(compareToMethod, method)) {
          foundCompareTo = true;
          break;
        }
      }
      if (!foundCompareTo) {
        return;
      }
      final PsiMethod[] equalsMethods = aClass.findMethodsByName(
        HardcodedMethodConstants.EQUALS, false);
      for (PsiMethod equalsMethod : equalsMethods) {
        if (MethodUtils.isEquals(equalsMethod)) {
          return;
        }
      }
      registerClassError(aClass);
    }
  }
}
