// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.testFrameworks;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.CommonClassNames.*;

public class MaskedAssertionInspection extends BaseInspection {

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("masked.assertion.problem.description", infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MaskedAssertionInspectionVisitor();
  }

  private static class MaskedAssertionInspectionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(PsiTryStatement statement) {
      super.visitTryStatement(statement);
      PsiElement lastPsiElement = getLastPsiElementInsideTryBlock(statement);
      if (lastPsiElement instanceof PsiAssertStatement) {
        registerError(lastPsiElement, "assert");
      }
      else if (lastPsiElement instanceof PsiExpressionStatement) {
        PsiExpression psiExpr = ((PsiExpressionStatement)lastPsiElement).getExpression();
        if (!(psiExpr instanceof PsiMethodCallExpression)) return;

        PsiMethodCallExpression psiMethodCallExpr = (PsiMethodCallExpression)psiExpr;
        while (true) {
          PsiMethodCallExpression currQualifierCall = MethodCallUtils.getQualifierMethodCall(psiMethodCallExpr);
          if (currQualifierCall == null) break;
          psiMethodCallExpr = currQualifierCall;
        }

        String methodRefName = psiMethodCallExpr.getMethodExpression().getReferenceName();
        if (methodRefName == null) return;
        if (methodRefName.equals("fail") || methodRefName.startsWith("assert")) {
          registerError(psiExpr, methodRefName + "()");
        }
      }
    }

    @Nullable
    private static PsiElement getLastPsiElementInsideTryBlock(@NotNull PsiTryStatement tryStatement) {
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();
      if (tryBlock == null) return null;
      PsiCatchSection[] catchSections = tryStatement.getCatchSections();
      if (catchSections.length == 0) return null;
      if (containsEmptyCatchBlockWithAssertionError(catchSections)) {
        PsiElement lastPsiElement = PsiTreeUtil.skipWhitespacesAndCommentsBackward(tryBlock.getLastBodyElement());
        if (lastPsiElement instanceof PsiAssertStatement) return lastPsiElement;
        return containsOnlyEmptyCatchBlocksWithAssertionErrorInheritors(catchSections) ? lastPsiElement : null;
      }
      return null;
    }

    private static boolean containsEmptyCatchBlockWithAssertionError(PsiCatchSection @NotNull [] catchSections) {
      for (String exceptionQn : Arrays.asList(JAVA_LANG_ASSERTION_ERROR, JAVA_LANG_ERROR, JAVA_LANG_THROWABLE)) {
        for (PsiCatchSection section : catchSections) {
          PsiType catchType = section.getCatchType();
          if (catchType == null) continue;
          List<PsiType> catchExceptionPsiTypes;
          if (catchType instanceof PsiDisjunctionType) {
            catchExceptionPsiTypes = ((PsiDisjunctionType)catchType).getDisjunctions();
          }
          else {
            catchExceptionPsiTypes = Collections.singletonList(catchType);
          }
          if (ContainerUtil.exists(catchExceptionPsiTypes, cet -> cet.equalsToText(exceptionQn))) {
            return ControlFlowUtils.isEmptyCodeBlock(section.getCatchBlock());
          }
        }
      }
      return false;
    }

    private static boolean containsOnlyEmptyCatchBlocksWithAssertionErrorInheritors(PsiCatchSection @NotNull [] catchSections) {
      PsiClassType assertionErrorType = PsiType.getTypeByName(JAVA_LANG_ASSERTION_ERROR, catchSections[0].getProject(),
                                                              catchSections[0].getResolveScope());
      for (PsiCatchSection section : catchSections) {
        PsiType catchType = section.getCatchType();
        boolean isAssertionErrorInheritor = catchType != null && !catchType.equalsToText(JAVA_LANG_ASSERTION_ERROR) &&
                                            assertionErrorType.isAssignableFrom(catchType);
        if (isAssertionErrorInheritor && !ControlFlowUtils.isEmptyCodeBlock(section.getCatchBlock())) return false;
      }
      return true;
    }
  }
}
