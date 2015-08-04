/*
 * Copyright 2011 Bas Leijdekkers
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
package com.siyeh.ig.dataflow;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

class BooleanVariableAlwaysNegatedInspectionBase extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "boolean.variable.always.inverted.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiVariable variable = (PsiVariable)infos[0];
    if (variable instanceof PsiField) {
      return InspectionGadgetsBundle.message(
        "boolean.field.always.inverted.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message(
        "boolean.variable.always.inverted.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BooleanVariableAlwaysNegatedVisitor();
  }

  private static class BooleanVariableAlwaysNegatedVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      if (!field.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (!isAlwaysInvertedBoolean(field, field.getContainingClass())) {
        return;
      }
      registerVariableError(field, field);
    }

    @Override
    public void visitLocalVariable(PsiLocalVariable variable) {
      super.visitLocalVariable(variable);
      final PsiCodeBlock codeBlock =
        PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
      if (!isAlwaysInvertedBoolean(variable, codeBlock)) {
        return;
      }
      registerVariableError(variable, variable);
    }

    private static boolean isAlwaysInvertedBoolean(PsiVariable field,
                                                   PsiElement context) {
      final PsiType type = field.getType();
      if (!PsiType.BOOLEAN.equals(type)) {
        return false;
      }
      final AlwaysNegatedVisitor visitor =
        new AlwaysNegatedVisitor(field);
      context.accept(visitor);
      return visitor.isRead() && visitor.isAlwaysNegated();
    }
  }

  private static class AlwaysNegatedVisitor
    extends JavaRecursiveElementWalkingVisitor {

    private final PsiVariable variable;
    private boolean alwaysNegated = true;
    private boolean read;

    private AlwaysNegatedVisitor(PsiVariable variable) {
      this.variable = variable;
    }

    @Override
    public void visitReferenceExpression(
      PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (!alwaysNegated) {
        return;
      }
      final String referenceName = expression.getReferenceName();
      if (referenceName == null) {
        return;
      }
      if (!referenceName.equals(variable.getName())) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!variable.equals(target)) {
        return;
      }
      if (!PsiUtil.isAccessedForReading(expression)) {
        return;
      }
      read = true;
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiPrefixExpression)) {
        alwaysNegated = false;
        return;
      }
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)parent;
      final IElementType tokenType =
        prefixExpression.getOperationTokenType();
      if (!JavaTokenType.EXCL.equals(tokenType)) {
        alwaysNegated = false;
      }
    }

    boolean isAlwaysNegated() {
      return alwaysNegated;
    }

    public boolean isRead() {
      return read;
    }
  }
}
