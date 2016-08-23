/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.asserttoif;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AssertionCanBeIfInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assertion.can.be.if.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return getDisplayName();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertToIfVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new AssertToIfFix();
  }

  private static void doFixImpl(@NotNull PsiElement element) {
    final PsiAssertStatement assertStatement = (PsiAssertStatement)element;
    @NonNls final StringBuilder newStatement = new StringBuilder();
    final PsiExpression condition = assertStatement.getAssertCondition();
    newStatement.append("if(").append(BoolUtils.getNegatedExpressionText(condition)).append(") throw new java.lang.AssertionError(");
    final PsiExpression description = assertStatement.getAssertDescription();
    if (description != null) {
      newStatement.append(description.getText());
    }
    newStatement.append(");");
    PsiReplacementUtil.replaceStatement(assertStatement, newStatement.toString());
  }

  private static class AssertToIfVisitor extends BaseInspectionVisitor {
    @Override
    public void visitAssertStatement(PsiAssertStatement assertStatement) {
      super.visitAssertStatement(assertStatement);
      if (assertStatement.getAssertCondition() != null) {
        registerError(assertStatement);
      }
    }
  }

  private static class AssertToIfFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("assert.can.be.if.quickfix");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      doFixImpl(descriptor.getPsiElement());
    }
  }
}