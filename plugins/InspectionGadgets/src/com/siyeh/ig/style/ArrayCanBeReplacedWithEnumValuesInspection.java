// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

/**
 * @author okli
 */
public class ArrayCanBeReplacedWithEnumValuesInspection extends BaseInspection {

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
      return new ArrayToEnumValueFix((String)infos[0]);
    }
    return null;
  }

  private static final class ArrayToEnumValueFix extends InspectionGadgetsFix {
    private final String myEnumName;

    private ArrayToEnumValueFix(String enumName) {
      myEnumName = enumName;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.quickfix", StringUtil.getShortName(myEnumName));
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("array.can.be.replaced.with.enum.values.family.quickfix");
    }


    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      if (myEnumName == null) {
        return;
      }
      final PsiElement element = descriptor.getPsiElement();
      if (element instanceof PsiNewExpression || element instanceof PsiArrayInitializerExpression) {
        PsiReplacementUtil.replaceExpression((PsiExpression)element, myEnumName + ".values()");
      }
    }
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

      final List<PsiEnumConstant> enumValues = ContainerUtil.filterIsInstance(Arrays.asList(initClass.getFields()), PsiEnumConstant.class);

      final PsiExpression[] initializers = expression.getInitializers();
      if (enumValues.size() != initializers.length) {
        return;
      }

      for (int i = 0; i < initializers.length; i++) {
        if (!ExpressionUtils.isReferenceTo(initializers[i], enumValues.get(i))) return;
      }

      final PsiElement parent = expression.getParent();
      final String enumName = initClass.getQualifiedName();

      if (parent instanceof PsiNewExpression) {
        registerError(parent, enumName);
      }
      else {
        registerError(expression, enumName);
      }
    }
  }
}

