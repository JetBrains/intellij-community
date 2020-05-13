/*
 * Copyright 2007-2017 Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class SynchronizedOnLiteralObjectInspection extends BaseInspection {

  @SuppressWarnings("PublicField") public boolean warnOnAllPossiblyLiterals = false;

  private static final Set<String> LITERAL_TYPES = ContainerUtil.set(
    CommonClassNames.JAVA_LANG_STRING,
    CommonClassNames.JAVA_LANG_BOOLEAN,
    CommonClassNames.JAVA_LANG_CHARACTER,
    CommonClassNames.JAVA_LANG_BYTE,
    CommonClassNames.JAVA_LANG_SHORT,
    CommonClassNames.JAVA_LANG_INTEGER,
    CommonClassNames.JAVA_LANG_LONG
  );

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final String typeText = ((PsiType)infos[0]).getPresentableText();
    final int message = ((Integer)infos[1]).intValue();
    switch (message) {
      case 1:
        return InspectionGadgetsBundle.message("synchronized.on.literal.object.problem.descriptor", typeText);
      case 2:
        return InspectionGadgetsBundle.message("synchronized.on.direct.literal.object.problem.descriptor", typeText);
      case 3:
        return InspectionGadgetsBundle.message("synchronized.on.possibly.literal.object.problem.descriptor", typeText);
      default:
        throw new AssertionError();
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("synchronized.on.literal.object.warn.on.all.option"),
                                          this, "warnOnAllPossiblyLiterals");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnLiteralVisitor();
  }

  private class SynchronizeOnLiteralVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = statement.getLockExpression();
      if (lockExpression == null) {
        return;
      }
      final PsiType type = lockExpression.getType();
      if (type == null) {
        return;
      }
      if (!LITERAL_TYPES.contains(type.getCanonicalText())) {
          return;
      }
      if (!(lockExpression instanceof PsiReferenceExpression)) {
        if (ExpressionUtils.isLiteral(lockExpression)) {
          registerError(lockExpression, type, Integer.valueOf(2));
        }
        else if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lockExpression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      final PsiVariable variable = (PsiVariable)target;
      final PsiExpression initializer = variable.getInitializer();
      if (!ExpressionUtils.isLiteral(initializer)) {
        if (warnOnAllPossiblyLiterals) {
          registerError(lockExpression, type, Integer.valueOf(3));
        }
        return;
      }
      registerError(lockExpression, type, Integer.valueOf(1));
    }
  }
}