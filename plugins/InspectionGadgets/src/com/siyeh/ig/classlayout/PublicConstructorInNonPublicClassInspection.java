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
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.fixes.RemoveModifierFix;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class PublicConstructorInNonPublicClassInspection extends MethodInspection {

  public String getGroupDisplayName() {
    return GroupNames.CLASSLAYOUT_GROUP_NAME;
  }

  public String buildErrorString(PsiElement location) {
    final PsiModifierList modifiers = (PsiModifierList)location.getParent();
    assert modifiers != null;
    final PsiMethod meth = (PsiMethod)modifiers.getParent();
    assert meth != null;
    return InspectionGadgetsBundle.message("public.constructor.in.non.public.class.problem.descriptor", meth.getName());
  }

  public BaseInspectionVisitor buildVisitor() {
    return new PublicConstructorInNonPublicClassVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return new RemoveModifierFix(location);
  }

  private static class PublicConstructorInNonPublicClassVisitor extends BaseInspectionVisitor {

    public void visitMethod(@NotNull PsiMethod method) {
      //no call to super, so we don't drill into anonymous classes
      if (!method.isConstructor()) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.hasModifierProperty(PsiModifier.PUBLIC)) {
        return;
      }
      registerModifierError(PsiModifier.PUBLIC, method);

    }

  }
}
