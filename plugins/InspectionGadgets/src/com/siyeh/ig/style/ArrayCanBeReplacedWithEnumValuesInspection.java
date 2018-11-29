// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiNewExpressionImpl;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author okli
 */
public class ArrayCanBeReplacedWithEnumValuesInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayCreationExpressionVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length == 1 && infos[0] instanceof String) {
      return new ArrayCreationExpressionVisitor.ArrayToEnumValueFix((String)infos[0]);
    }
    return null;
  }

  private static class ArrayCreationExpressionVisitor extends BaseInspectionVisitor {
    @Override
    public void visitArrayInitializerExpression(PsiArrayInitializerExpression expression) {
      super.visitArrayInitializerExpression(expression);

      final PsiType type = expression.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }

      final PsiType initExprType = ((PsiArrayType)type).getComponentType();
      final PsiClass initClass = PsiUtil.resolveClassInClassTypeOnly(initExprType);

      if (initClass == null || !initClass.isEnum()) {
        return;
      }

      final PsiExpression[] initializers = expression.getInitializers();
      int initL = initializers.length;

      List<String> enumValues = Stream.of(initClass.getFields())
        .filter(ev -> ev instanceof PsiEnumConstant)
        .map(ev -> (PsiEnumConstant) ev)
        .map(PsiEnumConstant::getName)
        .collect(Collectors.toList());


      if (enumValues.size() != initL) {
        return;
      }

      for (int i = 0; i < initL; i++) {
          String value = enumValues.get(i);
          //if (initializers[i] instanceof PsiMethodCallExpression && initL == 1) {
          //  PsiMethodCallExpression methodExpr = (PsiMethodCallExpression)initializers[i];
          //  PsiMethod methodR = methodExpr.resolveMethod();
          //  if (methodR != null) {
          //    PsiType returnV = methodR.getReturnType();
          //    if (!initExprType.equals(returnV)) {
          //      return;
          //    }
          //  }
          //}
          //else
            if (!(initializers[i] instanceof PsiReferenceExpression && initExprType.equals(initializers[i].getType()) && value.equals(((PsiReferenceExpression)initializers[i]).getReferenceName()))) {
            return;
        }
      }

      final PsiElement parent = expression.getParent();
      final String enumName = initClass.getName();

      if (parent instanceof PsiNewExpression) {
        registerError(parent, enumName);
      } else registerError(expression, enumName);
    }

    private static class ArrayToEnumValueFix extends InspectionGadgetsFix {
      private final String enumName;

      private ArrayToEnumValueFix(String enumType) {
        this.enumName = enumType;
      }

      @Nls
      @NotNull
      @Override
      public String getName() {
        return InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.quickfix", enumName);
      }

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.family.quickfix");
      }


      @Override
      protected void doFix(Project project, ProblemDescriptor descriptor) {
        if (enumName == null) {
          return;
        }
        final PsiElement element = descriptor.getPsiElement();
        if (element instanceof PsiNewExpressionImpl || element instanceof PsiArrayInitializerExpression) {
          final PsiExpression expression = (PsiExpression)element;
          PsiReplacementUtil.replaceExpression(expression, enumName + ".values()");
        }
      }
    }
  }
}

