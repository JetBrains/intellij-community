/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE filse.
 */
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ArrayObjectsEqualsInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("array.objects.equals.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final boolean deep = ((Boolean)infos[0]).booleanValue();
    return deep
           ? InspectionGadgetsBundle.message("array.objects.deep.equals.problem.descriptor")
           : InspectionGadgetsBundle.message("array.objects.equals.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final boolean deep = ((Boolean)infos[0]).booleanValue();
    return new ArrayObjectsEqualsFix(deep);
  }

  private static class ArrayObjectsEqualsFix extends InspectionGadgetsFix {

    private final boolean myDeep;

    public ArrayObjectsEqualsFix(boolean deep) {
      myDeep = deep;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myDeep ?
             InspectionGadgetsBundle.message("replace.with.arrays.deep.equals") :
             InspectionGadgetsBundle.message("replace.with.arrays.equals");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("replace.with.arrays.equals");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      final StringBuilder newExpression = new StringBuilder("java.util.Arrays.");
      if (myDeep) {
        newExpression.append("deepEquals");
      }
      else {
        newExpression.append("equals");
      }
      CommentTracker commentTracker = new CommentTracker();
      newExpression.append(commentTracker.text(methodCallExpression.getArgumentList()));
      PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, newExpression.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayObjectsEqualsVisitor();
  }

  private static class ArrayObjectsEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 2) {
        return;
      }
      final PsiExpression argument1 = expressions[0];
      final PsiType type1 = argument1.getType();
      if (!(type1 instanceof PsiArrayType)) {
        return;
      }
      final PsiExpression argument2 = expressions[1];
      final PsiType type2 = argument2.getType();
      if (!(type2 instanceof PsiArrayType)) {
        return;
      }
      final int dimensions = type1.getArrayDimensions();
      if (dimensions != type2.getArrayDimensions()) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || !"java.util.Objects".equals(containingClass.getQualifiedName())) {
        return;
      }
      registerMethodCallError(expression, Boolean.valueOf(dimensions > 1));
    }
  }
}
