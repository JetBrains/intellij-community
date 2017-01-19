/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.visibility;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

public class ClassEscapesItsScopeInspection extends BaseInspection {

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "ClassEscapesDefinedScope";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("class.escapes.defined.scope.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("class.escapes.defined.scope.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassEscapesItsScopeVisitor();
  }

  private static class ClassEscapesItsScopeVisitor extends BaseInspectionVisitor {
    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      PsiElement parent = reference.getParent();
      if (parent instanceof PsiTypeElement || parent instanceof PsiReferenceList) {
        PsiElement grandParent = PsiTreeUtil.skipParentsOfType(reference, PsiTypeElement.class, PsiReferenceList.class,
                                                               PsiParameter.class, PsiParameterList.class,
                                                               PsiReferenceParameterList.class, PsiJavaCodeReferenceElement.class,
                                                               PsiTypeParameter.class, PsiTypeParameterList.class);
        if (grandParent instanceof PsiField || grandParent instanceof PsiMethod) {
          PsiMember member = (PsiMember)grandParent;
          if (!isPrivate(member)) {
            PsiElement resolved = reference.resolve();
            if (resolved instanceof PsiClass && !(resolved instanceof PsiTypeParameter)) {
              PsiClass psiClass = (PsiClass)resolved;
              if (isLessRestrictiveScope(member, psiClass)) {
                registerError(reference);
              }
            }
          }
        }
      }
    }

    private static boolean isPrivate(@NotNull PsiMember member) {
      if (member.hasModifierProperty(PsiModifier.PRIVATE)) {
        return true;
      }
      PsiClass containingClass = member.getContainingClass();
      if (containingClass != null && isPrivate(containingClass)) {
        return true;
      }

      return false;
    }

    private static boolean isLessRestrictiveScope(@NotNull PsiMember member, @NotNull PsiClass aClass) {
      final int methodScopeOrder = getScopeOrder(member);
      final int classScopeOrder = getScopeOrder(aClass);
      final PsiClass containingClass = member.getContainingClass();
      if (containingClass == null ||
          containingClass.getQualifiedName() == null) {
        return false;
      }
      final int containingClassScopeOrder = getScopeOrder(containingClass);
      return methodScopeOrder > classScopeOrder && containingClassScopeOrder > classScopeOrder;
    }

    private static int getScopeOrder(@NotNull PsiModifierListOwner element) {
      if (element.hasModifierProperty(PsiModifier.PUBLIC)) {
        return 4;
      }
      else if (element.hasModifierProperty(PsiModifier.PRIVATE)) {
        return 1;
      }
      else if (element.hasModifierProperty(PsiModifier.PROTECTED)) {
        return 2;
      }
      else {
        return 3;
      }
    }
  }
}