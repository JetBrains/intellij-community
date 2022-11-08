// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.internal;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.uast.UastHintedVisitorAdapter;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.inspections.DevKitUastInspectionBase;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

public abstract class UseEqualsInspectionBase extends DevKitUastInspectionBase {

  @SuppressWarnings("unchecked")
  public static final Class<? extends UElement>[] HINTS = new Class[]{UBinaryExpression.class};

  @Override
  protected boolean isAllowed(@NotNull ProblemsHolder holder) {
    return super.isAllowed(holder) &&
           JavaPsiFacade.getInstance(holder.getProject())
             .findClass(getTargetClass().getName(), holder.getFile().getResolveScope()) != null;
  }

  protected abstract @NotNull Class<?> getTargetClass();

  @Override
  protected final @NotNull PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return UastHintedVisitorAdapter.create(holder.getFile().getLanguage(), new AbstractUastNonRecursiveVisitor() {

      @Override
      public boolean visitBinaryExpression(@NotNull UBinaryExpression binaryExpression) {
        UastBinaryOperator operator = binaryExpression.getOperator();
        if (isIdentityComparison(operator) && isNotExcluded(binaryExpression)) {
          PsiElement sourcePsi = binaryExpression.getSourcePsi();
          if (sourcePsi == null) return true;
          String operatorText = getOperatorText(binaryExpression);
          holder.registerProblem(sourcePsi,
                                 DevKitBundle.message("inspections.use.equals.description", getTargetClass().getSimpleName()),
                                 createFix(operatorText, operator == UastBinaryOperator.IDENTITY_NOT_EQUALS, sourcePsi.getLanguage())
          );
        }
        return true;
      }
    }, HINTS);
  }

  private static boolean isIdentityComparison(@NotNull UastBinaryOperator operator) {
    return operator == UastBinaryOperator.IDENTITY_EQUALS ||
           operator == UastBinaryOperator.IDENTITY_NOT_EQUALS;
  }

  private boolean isNotExcluded(@NotNull UBinaryExpression binaryExpression) {
    UExpression leftOperand = binaryExpression.getLeftOperand();
    if (isExcluded(leftOperand)) {
      return false;
    }

    UExpression rightOperand = binaryExpression.getRightOperand();
    if (isExcluded(rightOperand)) {
      return false;
    }

    return hasTargetType(leftOperand) || hasTargetType(rightOperand);
  }

  private static String getOperatorText(@NotNull UBinaryExpression binaryExpression) {
    UIdentifier identifier = binaryExpression.getOperatorIdentifier();
    if (identifier == null) return null;
    PsiElement identifierSourcePsi = identifier.getSourcePsi();
    if (identifierSourcePsi == null) return null;
    return identifierSourcePsi.getText();
  }

  protected boolean isExcluded(@NotNull UExpression operand) {
    return operand instanceof ULiteralExpression ||
           operand instanceof UThisExpression;
  }

  private boolean hasTargetType(@NotNull UExpression operand) {
    return operand.getSourcePsi() != null &&
           InheritanceUtil.isInheritor(operand.getExpressionType(), getTargetClass().getName());
  }

  private static LocalQuickFix @NotNull [] createFix(String operatorText, boolean negated, Language language) {
    if (language.is(JavaLanguage.INSTANCE)) {
      return new LocalQuickFix[]{new ReplaceIdentityWithEqualsComparisonFix(operatorText, negated)};
    }
    return LocalQuickFix.EMPTY_ARRAY;
  }

  private static class ReplaceIdentityWithEqualsComparisonFix implements LocalQuickFix {
    private final String myCurrentOperator;
    protected final boolean myNegated;

    ReplaceIdentityWithEqualsComparisonFix(String currentOperator, boolean negated) {
      myCurrentOperator = currentOperator;
      myNegated = negated;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression)) return;
      PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
      PsiExpression lOperand = binaryExpression.getLOperand();
      PsiExpression rOperand = binaryExpression.getROperand();
      if (rOperand == null) return;
      CommentTracker commentTracker = new CommentTracker();
      String lText = commentTracker.text(PsiUtil.skipParenthesizedExprUp(lOperand));
      String rText = commentTracker.text(PsiUtil.skipParenthesizedExprUp(rOperand));

      @NonNls String expString = (myNegated ? "!" : "") + lText + ".equals(" + rText + ')';
      PsiReplacementUtil.replaceExpression(binaryExpression, expString, commentTracker);
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myNegated ?
             DevKitBundle.message("inspections.use.equals.fix.replace.equality.with.not.equals.name", myCurrentOperator) :
             DevKitBundle.message("inspections.use.equals.fix.replace.equality.with.equals.name", myCurrentOperator);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return DevKitBundle.message("inspections.use.equals.fix.replace.equality.with.equals.family.name");
    }
  }
}
