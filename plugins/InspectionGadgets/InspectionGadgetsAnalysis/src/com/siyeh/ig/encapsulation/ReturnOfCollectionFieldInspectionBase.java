/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ReturnOfCollectionFieldInspectionBase extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignorePrivateMethods = true;

  @Override
  @NotNull
  public String getID() {
    return "ReturnOfCollectionOrArrayField";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("return.of.collection.array.field.display.name");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("return.of.collection.array.field.option"),
                                          this, "ignorePrivateMethods");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    final PsiType type = field.getType();
    if (type instanceof PsiArrayType) {
      return InspectionGadgetsBundle.message("return.of.collection.array.field.problem.descriptor.array");
    }
    else {
      return InspectionGadgetsBundle.message("return.of.collection.array.field.problem.descriptor.collection");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReturnOfCollectionFieldVisitor();
  }


  private class ReturnOfCollectionFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      final PsiExpression returnValue = statement.getReturnValue();
      if (returnValue == null) {
        return;
      }
      final PsiElement element = PsiTreeUtil.getParentOfType(statement, PsiMethod.class, PsiLambdaExpression.class);
      if (element == null) {
        return;
      }
      if (ignorePrivateMethods && element instanceof PsiMethod && ((PsiMethod)element).hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      final PsiClass returnStatementClass = PsiTreeUtil.getParentOfType(statement, PsiClass.class);
      if (returnStatementClass == null) {
        return;
      }
      if (!(returnValue instanceof PsiReferenceExpression)) {
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)returnValue;
      final PsiElement referent = referenceExpression.resolve();
      if (!(referent instanceof PsiField)) {
        return;
      }
      final PsiField field = (PsiField)referent;
      final PsiClass fieldClass = field.getContainingClass();
      if (!returnStatementClass.equals(fieldClass)) {
        return;
      }
      if (!CollectionUtils.isArrayOrCollectionField(field)) {
        return;
      }
      registerError(returnValue, field, returnValue);
    }
  }
}