/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.InstanceOfUtils;
import org.jetbrains.annotations.NotNull;

public class CastConflictsWithInstanceofInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "cast.conflicts.with.instanceof.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "cast.conflicts.with.instanceof.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new CastConflictsWithInstanceofVisitor();
    }


  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(final Object... infos) {
    final PsiInstanceOfExpression conflictingInstanceof = (PsiInstanceOfExpression)infos[2];
    return new InspectionGadgetsFix[] {new ReplaceFix(conflictingInstanceof, (PsiType)infos[0]){
      protected void replace(PsiTypeElement castTypeElement, PsiTypeElement instanceofTypeElement, Project project) {
        castTypeElement.replace(JavaPsiFacade.getElementFactory(project).createTypeElement(instanceofTypeElement.getType()));
      }

      @NotNull
      public String getName() {
        return "Replace cast to \'" + myCastType.getPresentableText() + "\' with \'" + myConflictingInstanceof.getCheckType().getType().getPresentableText() + "\'";
      }
    }, new ReplaceFix(conflictingInstanceof, (PsiType)infos[0]) {
      protected void replace(PsiTypeElement castTypeElement, PsiTypeElement instanceofTypeElement, Project project) {
        instanceofTypeElement.replace(JavaPsiFacade.getElementFactory(project).createTypeElement(castTypeElement.getType()));
      }

      @NotNull
      public String getName() {
        return "Replace instanceof \'" + myConflictingInstanceof.getCheckType().getType().getPresentableText() + "\' with \'" + myCastType.getPresentableText() + "\'";
      }
    }};
  }

  private static class CastConflictsWithInstanceofVisitor
            extends BaseInspectionVisitor {

        @Override public void visitTypeCastExpression(
                @NotNull PsiTypeCastExpression expression) {
            super.visitTypeCastExpression(expression);
            final PsiType castType = expression.getType();
            if (castType != null) {
              final PsiExpression operand = expression.getOperand();
              final PsiInstanceOfExpression conflictingInstanceof = InstanceOfUtils.getConflictingInstanceof(expression);
              if (conflictingInstanceof == null) {
                return;
              }
              registerError(expression, castType, operand, conflictingInstanceof);
            }
        }
    }

  private static abstract class ReplaceFix extends InspectionGadgetsFix {
    protected final PsiInstanceOfExpression myConflictingInstanceof;
    protected final PsiType myCastType;

    public ReplaceFix(PsiInstanceOfExpression conflictingInstanceof, PsiType castType) {
      myConflictingInstanceof = conflictingInstanceof;
      myCastType = castType;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiTypeCastExpression typeCastExpression = (PsiTypeCastExpression)descriptor.getPsiElement();
      final PsiTypeElement castTypeElement = typeCastExpression.getCastType();
      final PsiTypeElement typeElement = myConflictingInstanceof.getCheckType();
      if (castTypeElement != null && typeElement != null) {
        replace(castTypeElement, typeElement, project);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(myConflictingInstanceof);
      }
    }

    protected abstract void replace(PsiTypeElement castTypeElement, PsiTypeElement instanceofTypeElement, Project project);


  }


}