// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.testFrameworks.AssertHint.JUnitCommonAssertNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.psi.CommonClassNames.*;

public class RedundantAssertionInspection extends BaseInspection {

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("redundant.assertion.problem.description");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new RedundantAssertionInspectionVisitor();
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveRedundantAssertionFix();
  }

  private static class RedundantAssertionInspectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiElement lastElement = getLastPsiElementInsideOuterTryBlock(expression, true);
      if (lastElement != expression.getParent()) return;
      if (!(lastElement instanceof PsiExpressionStatement)) return;

      PsiExpression psiExpr = ((PsiExpressionStatement)lastElement).getExpression();
      if (!(psiExpr instanceof PsiMethodCallExpression)) return;

      PsiMethodCallExpression psiMethodCallExpr = (PsiMethodCallExpression)psiExpr;
      if (AssertBetweenInconvertibleTypesInspection.getAssertThatMethodCall(psiMethodCallExpr).isPresent()) {
        registerError(expression);
      }
      else {
        AssertHint assertHint = AssertHint.create(psiMethodCallExpr,
                                                  methodName -> JUnitCommonAssertNames.ASSERT_METHOD_2_PARAMETER_COUNT.get(methodName));
        if (assertHint != null) {
          registerError(psiExpr);
        }
      }
    }

    @Override
    public void visitAssertStatement(PsiAssertStatement statement) {
      super.visitAssertStatement(statement);
      if (statement == getLastPsiElementInsideOuterTryBlock(statement, false)) {
        registerError(statement);
      }
    }

    @Nullable
    private static PsiElement getLastPsiElementInsideOuterTryBlock(@NotNull PsiElement psiElement, boolean checkAssertionErrorInheritors) {
      PsiTryStatement tryStatement = PsiTreeUtil.getParentOfType(psiElement, PsiTryStatement.class);
      if (tryStatement == null) return null;
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) return null;
      PsiCatchSection[] catchSections = tryStatement.getCatchSections();
      if (catchSections.length == 0) return null;
      if (containsEmptyCatchBlock(catchSections)) {
        if (checkAssertionErrorInheritors && containsAssertionErrorInheritor(catchSections, tryStatement.getProject())) return null;
        return PsiTreeUtil.skipWhitespacesAndCommentsBackward(tryBlock.getLastBodyElement());
      }
      return null;
    }

    private static boolean containsEmptyCatchBlock(PsiCatchSection @NotNull [] catchSections) {
      for (String exceptionQn : Arrays.asList(JAVA_LANG_ASSERTION_ERROR, JAVA_LANG_ERROR, JAVA_LANG_THROWABLE)) {
        for (PsiCatchSection section : catchSections) {
          PsiType catchType = section.getCatchType();
          if (catchType == null || !catchType.equalsToText(exceptionQn)) continue;
          PsiCodeBlock catchBlock = section.getCatchBlock();
          return catchBlock != null && catchBlock.getStatementCount() == 0;
        }
      }
      return false;
    }

    private static boolean containsAssertionErrorInheritor(PsiCatchSection @NotNull [] catchSections, @NotNull Project project) {
      PsiClassType assertionErrorType = PsiType.getTypeByName(JAVA_LANG_ASSERTION_ERROR, project, GlobalSearchScope.allScope(project));
      for (PsiCatchSection section : catchSections) {
        PsiType catchType = section.getCatchType();
        if (catchType != null && !catchType.equalsToText(JAVA_LANG_ASSERTION_ERROR) && assertionErrorType.isAssignableFrom(catchType)) {
          return true;
        }
      }
      return false;
    }
  }

  private static class RemoveRedundantAssertionFix extends InspectionGadgetsFix {

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      PsiElement psiElement = descriptor.getPsiElement();
      if (psiElement != null) {
        psiElement.delete();
      }
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("redundant.assertion.quick.fix");
    }
  }
}
