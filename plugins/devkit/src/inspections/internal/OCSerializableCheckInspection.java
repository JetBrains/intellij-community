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
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;

/**
 * Created by Max Medvedev on 07/12/14
 */
public class OCSerializableCheckInspection extends InternalInspection {

  public static final String SERIALIZABLE = "com.jetbrains.objc.symbols.Serializable";

  @Override
  public PsiElementVisitor buildInternalVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        super.visitNewExpression(expression);

        PsiMethod constructor = expression.resolveConstructor();
        if (constructor != null && constructor.getParameterList().getParametersCount() == 0) {
          checkSerializable(constructor, expression);
        }
      }

      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);

        PsiElement resolved = expression.resolve();
        if (resolved instanceof PsiField) {
          PsiElement nameElement = expression.getReferenceNameElement();
          if (nameElement != null) {
            checkSerializable((PsiField)resolved, nameElement);
          }
        }
      }

      private void checkSerializable(@NotNull PsiMember member, @NotNull PsiElement place) {
        if (member.hasModifierProperty(PsiModifier.PUBLIC) && !isInInitializer(place)) {
          PsiClass aClass = member.getContainingClass();
          if (aClass != null && !PsiTreeUtil.isAncestor(aClass, place, true)) {
            PsiModifierList modifierList = aClass.getModifierList();
            if (modifierList != null && modifierList.findAnnotation(SERIALIZABLE) != null) {
              String message = DevKitBundle.message("serialization.only.member.used.explicitly");
              holder.registerProblem(place, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
          }
        }
      }
    };
  }

  private static boolean isInInitializer(PsiElement place) {
    while (true) {
      place = PsiTreeUtil.getParentOfType(place, PsiClass.class);
      if (place == null) return false;
      String name = ((PsiClass)place).getName();
      if (name != null && name.contains("Serializer")) {
        return true;
      }
    }
  }
}
