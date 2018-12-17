// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
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
      return new ArrayToEnumValueFix((String)infos[0]);
    }
    return null;
  }

  private static class ArrayToEnumValueFix extends InspectionGadgetsFix {
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

      final List<String> enumValues = Stream.of(initClass.getFields())
        .filter(ev -> ev instanceof PsiEnumConstant)
        .map(ev -> ev.getName())
        .collect(Collectors.toList());

      final PsiExpression[] initializers = expression.getInitializers();
      if (enumValues.size() != initializers.length) {
        return;
      }

      for (int i = 0; i < initializers.length; i++) {
        if (!(initializers[i] instanceof PsiReferenceExpression &&
              enumValues.get(i).equals(((PsiReferenceExpression)initializers[i]).getReferenceName()) &&
              initExprType.equals(initializers[i].getType()))) {
          return;
        }
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

