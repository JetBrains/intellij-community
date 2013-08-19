/*
 * Copyright 2010-2012 Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class UnnecessarilyQualifiedStaticallyImportedElementInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessarily.qualified.statically.imported.element.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiMember member = (PsiMember)infos[0];
    return InspectionGadgetsBundle.message("unnecessarily.qualified.statically.imported.element.problem.descriptor", member.getName());
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessarilyQualifiedStaticallyImportedElementFix();
  }

  private static class UnnecessarilyQualifiedStaticallyImportedElementFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessarily.qualified.statically.imported.element.quickfix");
    }
  @Override
  @NotNull
  public String getFamilyName() {
    return getName();
  }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      element.delete();
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessarilyQualifiedStaticallyImportedElementVisitor();
  }

  private static class UnnecessarilyQualifiedStaticallyImportedElementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement qualifier = reference.getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(reference, PsiReferenceExpression.class, PsiImportStatementBase.class) != null) {
        return;
      }
      if (UnnecessarilyQualifiedStaticUsageInspection.isGenericReference(reference, (PsiJavaCodeReferenceElement)qualifier)) return;
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiMember)) {
        return;
      }
      final PsiMember member = (PsiMember)target;
      final PsiJavaCodeReferenceElement referenceExpression = (PsiJavaCodeReferenceElement)qualifier;
      final PsiElement qualifierTarget = referenceExpression.resolve();
      if (!(qualifierTarget instanceof PsiClass)) {
        return;
      }
      if (!ImportUtils.isStaticallyImported(member, reference)) {
        return;
      }
      if (!isReferenceCorrectWithoutQualifier(reference, member)) {
        return;
      }
      registerError(qualifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL, member);
    }

    private static boolean isReferenceCorrectWithoutQualifier(
      PsiJavaCodeReferenceElement reference, PsiMember member) {
      final String referenceName = reference.getReferenceName();
      if (referenceName == null) {
        return false;
      }
      final Project project = reference.getProject();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiResolveHelper resolveHelper = psiFacade.getResolveHelper();
      if (member instanceof PsiMethod) {
        final PsiElementFactory factory = psiFacade.getElementFactory();
        final PsiExpression expression = factory.createExpressionFromText(referenceName + "()", reference);
        final CandidateInfo[] methodCandidates = resolveHelper.getReferencedMethodCandidates((PsiCallExpression)expression, false);
        for (CandidateInfo methodCandidate : methodCandidates) {
          if (!(methodCandidate.getCurrentFileResolveScope() instanceof PsiImportStaticStatement)) {
            return false;
          }
        }
      }
      else if (member instanceof PsiField) {
        final PsiVariable variable = resolveHelper.resolveAccessibleReferencedVariable(referenceName, reference);
        if (!member.equals(variable)) {
          return false;
        }
      }
      else if (member instanceof PsiClass) {
        final PsiClass aClass = resolveHelper.resolveReferencedClass(referenceName, reference);
        if (!member.equals(aClass)) {
          return false;
        }
      }
      return true;
    }
  }
}
