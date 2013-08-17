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
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class NonThreadSafeLazyInitializationInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "non.thread.safe.lazy.initialization.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "non.thread.safe.lazy.initialization.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnsafeSafeLazyInitializationVisitor();
  }

  private static class UnsafeSafeLazyInitializationVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(
      @NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final PsiExpression lhs = expression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReference reference = (PsiReference)lhs;
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (isInStaticInitializer(expression)) {
        return;
      }
      if (isInSynchronizedContext(expression)) {
        return;
      }
      if (!isLazy(expression, (PsiReferenceExpression)lhs)) {
        return;
      }
      boolean assignedOnce = isAssignedOnce(referent);
      boolean safeToDelete = isSafeToDeleteIfStatement(expression);
      registerError(lhs, assignedOnce && safeToDelete);
    }

    private static boolean isAssignedOnce(PsiElement referent) {
      final int[] writeCount = new int[1];
      return ReferencesSearch.search(referent).forEach(new Processor<PsiReference>() {
        @Override
        public boolean process(PsiReference reference) {
          PsiElement element = reference.getElement();
          if (!(element instanceof PsiExpression)) {
            return true;
          }
          if (!PsiUtil.isAccessedForWriting((PsiExpression)element)) {
            return true;
          }
          return ++writeCount[0] != 2;
        }
      });
    }

    private static boolean isSafeToDeleteIfStatement(PsiElement expression) {
      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class);
      if (ifStatement.getElseBranch() != null) {
        return false;
      }
      PsiStatement thenBranch = ifStatement.getThenBranch();
      if (thenBranch == null) return false;
      if (!(thenBranch instanceof PsiBlockStatement)) {
        return true;
      }
      return ((PsiBlockStatement)thenBranch).getCodeBlock().getStatements().length == 1;
    }

    private static boolean isLazy(PsiAssignmentExpression expression,
                                  PsiReferenceExpression lhs) {
      final PsiIfStatement ifStatement =
        PsiTreeUtil.getParentOfType(expression,
                                    PsiIfStatement.class);
      if (ifStatement == null) {
        return false;
      }
      final PsiExpression condition = ifStatement.getCondition();
      if (condition == null) {
        return false;
      }
      return isNullComparison(condition, lhs);
    }

    private static boolean isNullComparison(
      PsiExpression condition, PsiReferenceExpression reference) {
      if (!(condition instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression comparison =
        (PsiBinaryExpression)condition;
      final IElementType tokenType = comparison.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.EQEQ)) {
        return false;
      }
      final PsiExpression lhs = comparison.getLOperand();
      final PsiExpression rhs = comparison.getROperand();
      if (rhs == null) {
        return false;
      }
      final String lhsText = lhs.getText();
      final String rhsText = rhs.getText();
      if (!PsiKeyword.NULL.equals(lhsText) &&
          !PsiKeyword.NULL.equals(rhsText)) {
        return false;
      }
      final String referenceText = reference.getText();
      return referenceText.equals(lhsText) ||
             referenceText.equals(rhsText);
    }

    private static boolean isInSynchronizedContext(PsiElement element) {
      final PsiSynchronizedStatement syncBlock =
        PsiTreeUtil.getParentOfType(element,
                                    PsiSynchronizedStatement.class);
      if (syncBlock != null) {
        return true;
      }
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(element,
                                    PsiMethod.class);
      return method != null &&
             method.hasModifierProperty(PsiModifier.SYNCHRONIZED)
             && method.hasModifierProperty(PsiModifier.STATIC);
    }

    private static boolean isInStaticInitializer(PsiElement element) {
      final PsiClassInitializer initializer =
        PsiTreeUtil.getParentOfType(element,
                                    PsiClassInitializer.class);
      return initializer != null &&
             initializer.hasModifierProperty(PsiModifier.STATIC);
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    boolean isApplicable = ((Boolean)infos[0]).booleanValue();
    return isApplicable ? new IntroduceHolderFix() : null;
  }

  private static class IntroduceHolderFix extends InspectionGadgetsFix {
    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiReferenceExpression expression = (PsiReferenceExpression)descriptor.getPsiElement();
      PsiElement resolved = expression.resolve();
      if (!(resolved instanceof PsiField)) return;
      PsiField field = (PsiField)resolved;
      String holderName = suggestHolderName(field);
      @NonNls String text = "private static class " + holderName
                            + " {" +
                            "private static final " + field.getType().getCanonicalText() + " " +
                            field.getName() + " = " + ((PsiAssignmentExpression)expression.getParent()).getRExpression().getText() + ";"
                            + "}";
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(field.getProject()).getElementFactory();
      PsiClass holder = elementFactory.createClassFromText(text, field).getInnerClasses()[0];
      PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
      method.getParent().addBefore(holder, method);

      PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class);
      ifStatement.delete();

      final PsiExpression holderReference = elementFactory.createExpressionFromText(holderName + "." + field.getName(), field);
      Collection<PsiReference> references = ReferencesSearch.search(field).findAll();
      for (PsiReference reference : references) {
        PsiElement element = reference.getElement();
        element.replace(holderReference);
      }
      field.delete();
    }

    @NonNls
    private static String suggestHolderName(PsiField field) {
      String string = field.getType().getDeepComponentType().getPresentableText();
      final int index = string.indexOf('<');
      if (index != -1) {
        string = string.substring(0, index);
      }
      return string + "Holder";
    }

    @Override
    @NotNull
    public String getName() {
      return "Introduce holder class";
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }
  }
}