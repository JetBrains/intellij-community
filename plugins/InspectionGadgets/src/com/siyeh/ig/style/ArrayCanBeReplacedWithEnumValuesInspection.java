// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

      final PsiType initType = expression.getType().getDeepComponentType();
      final PsiClass initClass = PsiUtil.resolveClassInClassTypeOnly(initType);

      if (initClass == null || !initClass.isEnum()) {
        return;
      }

      List<String> enumValues = StreamEx.of(initClass.getAllFields())
        .select(PsiEnumConstant.class)
        .map(PsiEnumConstant::getName)
        .toCollection(LinkedList::new);

      if (enumValues == null) {
        return;
      }


      final PsiExpression[] initializers = expression.getInitializers();

      int nValues = enumValues.size();
      if (nValues != initializers.length) {
        return;
      }

      int i = 0;
      for (String value : enumValues) {
        String referenceName = ((PsiReferenceExpressionImpl)initializers[i]).getReferenceName();
        if (referenceName !=null && referenceName.equals(value)) {
          i++;
        }
        else {
          return;
        }
      }

      final PsiElement parent = expression.getParent();
      if (i == nValues) {
        final String typeText = ((PsiArrayType)type).getComponentType().getPresentableText();
        registerError(parent, typeText);
      }
    }

    private static class ArrayToEnumValueFix extends InspectionGadgetsFix {
      private final String myType;

      private ArrayToEnumValueFix(String enumType) {
        this.myType = enumType;
      }

      @Nls
      @NotNull
      @Override
      public String getName() {
        return InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.quickfix", myType);
      }

      @Nls(capitalization = Nls.Capitalization.Sentence)
      @NotNull
      @Override
      public String getFamilyName() {
        return InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.family.quickfix");
      }


      @Override
      protected void doFix(Project project, ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        if (element instanceof PsiNewExpression) {
          final PsiNewExpression newExpression = (PsiNewExpression)element;
          final PsiType type = newExpression.getType();
          if (type != null) {
            final String enumType = ((PsiArrayType)type).getComponentType().getPresentableText();
            PsiReplacementUtil.replaceExpression(newExpression, enumType + ".values()");
          }
        }
      }
    }
  }
}

