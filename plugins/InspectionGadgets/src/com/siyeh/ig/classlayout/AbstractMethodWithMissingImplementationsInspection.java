/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;

public class AbstractMethodWithMissingImplementationsInspection extends MethodInspection {

  public String getGroupDisplayName() {
    return GroupNames.INHERITANCE_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new AbstactMethodWithMissingImplementationsVisitor();
  }

  private static class AbstactMethodWithMissingImplementationsVisitor
    extends BaseInspectionVisitor {
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!containingClass.isInterface() &&
          !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final PsiManager psiManager = containingClass.getManager();
      final PsiSearchHelper searchHelper = psiManager.getSearchHelper();
      final SearchScope searchScope = containingClass.getUseScope();
      final PsiClass[] inheritors =
        searchHelper.findInheritors(containingClass, searchScope,
                                    true);
      for (final PsiClass inheritor : inheritors) {
        if (!inheritor.isInterface() &&
            !inheritor.hasModifierProperty(PsiModifier.ABSTRACT)) {
          if (!hasMatchingImplementation(inheritor, method)) {
            registerMethodError(method);
            return;
          }
        }
      }
    }

    private static boolean hasMatchingImplementation(PsiClass aClass,
                                                     PsiMethod method) {
      final PsiMethod[] methods = aClass.findMethodsBySignature(method, true);
      for (final PsiMethod methodToMatch : methods) {
        if (!methodToMatch.hasModifierProperty(PsiModifier.ABSTRACT) &&
            !methodToMatch.getContainingClass().isInterface()) {
          return true;
        }
      }
      return false;
    }
  }
}
