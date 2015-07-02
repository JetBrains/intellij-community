/*
 * Copyright 2009-2015 Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class FinalUtils {

  private FinalUtils() {}

  public static boolean canBeFinal(@NotNull PsiVariable variable) {
    if (variable.getInitializer() != null || variable instanceof PsiParameter) {
      // parameters have an implicit initializer
      return !VariableAccessUtils.variableIsAssigned(variable);
    }
    final FinalDefiniteAssignment definiteAssignment = new FinalDefiniteAssignment(variable);
    DefiniteAssignmentUtil.checkVariable(variable, definiteAssignment);
    return definiteAssignment.isDefinitelyAssigned() &&
           !definiteAssignment.isDefinitelyUnassigned() && // spec?
           definiteAssignment.canBeFinal() &&
           !isWrittenToOutsideOfConstruction(variable);
  }

  private static boolean isWrittenToOutsideOfConstruction(PsiVariable variable) {
    if (!(variable instanceof PsiField)) {
      return false;
    }
    final PsiField field = (PsiField)variable;
    final PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final PsiClass topLevelClass = PsiUtil.getTopLevelClass(variable);
    final VariableAssignedVisitor visitor = new VariableAssignedVisitor(field);
    if (topLevelClass != null && !containingClass.equals(topLevelClass)) {
      visitor.setExcludedElement(containingClass);
      topLevelClass.accept(visitor);
      if (visitor.isAssigned()) {
        return true;
      }
    }
    if (field.hasModifierProperty(PsiModifier.STATIC)) {
      for (PsiElement child : containingClass.getChildren()) {
        if (child instanceof PsiClassInitializer) {
          final PsiClassInitializer classInitializer = (PsiClassInitializer)child;
          if (classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          classInitializer.accept(visitor);
        }
        else if (child instanceof PsiField) {
          final PsiField otherField = (PsiField)child;
          if (otherField.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          otherField.accept(visitor);
        }
        else if (child instanceof PsiMethod || child instanceof PsiClass) {
          child.accept(visitor);
        }
        if (visitor.isAssigned()) {
          return true;
        }
      }
    }
    else {
      for (PsiElement child : containingClass.getChildren()) {
        if (child instanceof PsiField) {
          final PsiField otherField = (PsiField)child;
          if (!otherField.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          otherField.accept(visitor);
        }
        else if (child instanceof PsiClassInitializer) {
          final PsiClassInitializer classInitializer = (PsiClassInitializer)child;
          if (!classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
            continue;
          }
          classInitializer.accept(visitor);
        }
        else if (child instanceof PsiMethod) {
          final PsiMethod method = (PsiMethod)child;
          if (method.isConstructor()) {
            continue;
          }
          method.accept(visitor);
        }
        else if (child instanceof PsiClass) {
          child.accept(visitor);
        }
        if (visitor.isAssigned()) {
          return true;
        }
      }
    }
    return false;
  }

  private static class FinalDefiniteAssignment extends DefiniteAssignment {

    private boolean canBeFinal = true;

    public FinalDefiniteAssignment(PsiVariable variable) {
      super(variable);
    }

    @Override
    public void assign(@NotNull PsiReferenceExpression expression, boolean definiteAssignment) {
      if (!isDefinitelyUnassigned()) {
        canBeFinal = false;
      }
      super.assign(expression, definiteAssignment);
    }

    @Override
    public void valueAccess(PsiReferenceExpression expression) {
      if (!isDefinitelyAssigned()) {
        canBeFinal = false;
      }
      super.valueAccess(expression);
    }

    @Override
    public boolean stop() {
      return !canBeFinal;
    }

    public boolean canBeFinal() {
      return canBeFinal;
    }
  }
}