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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class ClassEscapesItsScopeInspection extends BaseInspection {

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
    public void visitMethod(@NotNull PsiMethod method) {
      //no call to super, so we don't drill into anonymous classes
      if (method.isConstructor()) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      checkForEscaping(method, method.getReturnType(), method.getReturnTypeElement());
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      //no call to super, so we don't drill into anonymous classes
      if (field.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      checkForEscaping(field, field.getType(), field.getTypeElement());
    }

    private void checkForEscaping(PsiMember member, PsiType type, PsiTypeElement typeElement) {
      if (type == null || typeElement == null) {
        return;
      }
      final PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiClassType)) {
        return;
      }
      final PsiClass fieldClass = ((PsiClassType)componentType).resolve();
      if (fieldClass == null || fieldClass instanceof PsiTypeParameter) {
        return;
      }
      if (!isLessRestrictiveScope(member, fieldClass)) {
        return;
      }

      final PsiJavaCodeReferenceElement baseTypeElement = typeElement.getInnermostComponentReferenceElement();
      if (baseTypeElement == null) {
        return;
      }
      registerError(baseTypeElement);
    }

    private static boolean isLessRestrictiveScope(PsiMember method, PsiClass aClass) {
      final int methodScopeOrder = getScopeOrder(method);
      final int classScopeOrder = getScopeOrder(aClass);
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass != null && containingClass.getQualifiedName() == null) {
        return false;
      }
      final int containingClassScopeOrder = getScopeOrder(containingClass);
      return methodScopeOrder > classScopeOrder && containingClassScopeOrder > classScopeOrder;
    }

    private static int getScopeOrder(PsiModifierListOwner element) {
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