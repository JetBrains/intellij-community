/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class CovariantCompareToInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("covariant.compareto.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("covariant.compareto.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CovariantCompareToVisitor();
  }

  private static class CovariantCompareToVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      final String name = method.getName();
      if (!HardcodedMethodConstants.COMPARE_TO.equals(name)) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType paramType = parameters[0].getType();
      if (TypeUtils.isJavaLangObject(paramType)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final PsiMethod[] methods = aClass.findMethodsByName(HardcodedMethodConstants.COMPARE_TO, false);
      final Project project = method.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final GlobalSearchScope scope = method.getResolveScope();
      final PsiClass comparableClass = psiFacade.findClass(CommonClassNames.JAVA_LANG_COMPARABLE, scope);
      PsiType substitutedTypeParam = null;
      if (comparableClass != null && comparableClass.getTypeParameters().length == 1) {
        final PsiSubstitutor superSubstitutor = TypeConversionUtil.getClassSubstitutor(comparableClass, aClass, PsiSubstitutor.EMPTY);
        //null iff aClass is not inheritor of comparableClass
        if (superSubstitutor != null) {
           substitutedTypeParam = superSubstitutor.substitute(comparableClass.getTypeParameters()[0]);
        }
      }
      for (PsiMethod compareToMethod : methods) {
        if (isNonVariantCompareTo(compareToMethod, substitutedTypeParam)) {
          return;
        }
      }
      registerMethodError(method);
    }

    private static boolean isNonVariantCompareTo(PsiMethod method, PsiType substitutedTypeParam) {
      final PsiClassType objectType = TypeUtils.getObjectType(method);
      if (MethodUtils.methodMatches(method, null, PsiType.INT, HardcodedMethodConstants.COMPARE_TO, objectType)) {
        return true;
      }
      if (substitutedTypeParam == null) {
        return false;
      }
      return MethodUtils.methodMatches(method, null, PsiType.INT, HardcodedMethodConstants.COMPARE_TO, substitutedTypeParam);
    }
  }
}