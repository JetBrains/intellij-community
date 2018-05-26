// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.dataFlow.Mutability;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class AssignmentOrReturnOfFieldWithMutableTypeInspection extends BaseInspection {

  public static final String[] MUTABLE_TYPES = {
    CommonClassNames.JAVA_UTIL_DATE,
    CommonClassNames.JAVA_UTIL_CALENDAR,
    CommonClassNames.JAVA_UTIL_COLLECTION,
    CommonClassNames.JAVA_UTIL_MAP,
    "com.google.common.collect.Multimap",
    "com.google.common.collect.Table"
  };

  @SuppressWarnings("PublicField")
  public boolean ignorePrivateMethods = true;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assignment.or.return.of.field.with.mutable.type.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    final PsiExpression rhs = (PsiExpression)infos[1];
    final PsiType type = field.getType();
    final boolean assignment = ((Boolean)infos[3]).booleanValue();
    return assignment
           ? InspectionGadgetsBundle.message("assignment.of.field.with.mutable.type.problem.descriptor",
                                             type.getPresentableText(), field.getName(), rhs.getText())
           : InspectionGadgetsBundle.message("return.of.field.with.mutable.type.problem.descriptor",
                                             type.getPresentableText(), field.getName());
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiReferenceExpression returnValue = (PsiReferenceExpression)infos[1];
    final String type = (String)infos[2];
    if (CommonClassNames.JAVA_UTIL_DATE.equals(type) ||
        CommonClassNames.JAVA_UTIL_CALENDAR.equals(type) ||
        returnValue.getType() instanceof PsiArrayType)  {
      final PsiField field = (PsiField)infos[0];
      return new ReplaceWithCloneFix(field.getName());
    }
    final boolean assignment = ((Boolean)infos[3]).booleanValue();
    if (!assignment) {
      return ReturnOfCollectionFieldFix.build(returnValue);
    }
    return null;
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("ignore.private.methods.option"), this, "ignorePrivateMethods");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssignmentOrReturnOfFieldWithMutableTypeVisitor();
  }

  private class AssignmentOrReturnOfFieldWithMutableTypeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.EQ.equals(tokenType)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.deparenthesizeExpression(expression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression)) {
        return;
      }
      final String type = TypeUtils.expressionHasTypeOrSubtype(lhs, MUTABLE_TYPES);
      if (type == null && !(lhs.getType() instanceof PsiArrayType)) {
        return;
      }
      final PsiExpression rhs = PsiUtil.deparenthesizeExpression(expression.getRExpression());
      if (!(rhs instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiField field = ObjectUtils.tryCast(((PsiReference)lhs).resolve(), PsiField.class);
      if (field == null) return;
      final PsiParameter parameter = ObjectUtils.tryCast(((PsiReference)rhs).resolve(), PsiParameter.class);
      if (parameter == null || !(parameter.getDeclarationScope() instanceof PsiMethod) || ClassUtils.isImmutable(parameter.getType())) {
        return;
      }
      if (ignorePrivateMethods) {
        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        if (containingMethod == null || containingMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        if (containingMethod.isConstructor()) {
          final PsiClass containingClass = containingMethod.getContainingClass();
          if (containingClass != null && containingClass.hasModifierProperty(PsiModifier.PRIVATE)) {
            return;
          }
        }
      }
      registerError(rhs, field, rhs, type, Boolean.TRUE);
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression returnValue = PsiUtil.deparenthesizeExpression(statement.getReturnValue());
      if (!(returnValue instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiElement element = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
      if (ignorePrivateMethods && element instanceof PsiMethod && ((PsiMethod)element).hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiField field = ObjectUtils.tryCast(((PsiReferenceExpression)returnValue).resolve(), PsiField.class);
      if (field == null) return;
      final String type = TypeUtils.expressionHasTypeOrSubtype(returnValue, MUTABLE_TYPES);
      if (type == null && !(returnValue.getType() instanceof PsiArrayType)) return;
      if (CollectionUtils.isConstantEmptyArray(field) || Mutability.getMutability(field).isUnmodifiable()) return;
      registerError(returnValue, field, returnValue, type, Boolean.FALSE);
    }
  }
}
