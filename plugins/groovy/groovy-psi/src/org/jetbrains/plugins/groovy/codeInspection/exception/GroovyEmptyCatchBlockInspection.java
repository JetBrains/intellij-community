/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.exception;

import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrCatchClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import javax.swing.*;

public class GroovyEmptyCatchBlockInspection extends BaseInspection {
  public boolean myIgnore = true;
  public boolean myCountCommentsAsContent = true;
  @NlsSafe private static final String NEW_NAME = "ignored";

  @Override
  @NotNull
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  @Nullable
  @Override
  public JComponent createGroovyOptionsPanel() {
    MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(GroovyBundle.message("comments.count.as.content"), "myCountCommentsAsContent");
    panel.addCheckbox(GroovyBundle.message("ignore.when.catch.parameter.is.named.ignore.or.ignored"), "myIgnore");
    return panel;
  }

  private class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitCatchClause(@NotNull GrCatchClause catchClause) {
      super.visitCatchClause(catchClause);
      final GrOpenBlock body = catchClause.getBody();
      if (body == null || !isEmpty(body)) {
        return;
      }

      final GrParameter parameter = catchClause.getParameter();
      if (parameter == null) return;
      if (myIgnore && GrExceptionUtil.ignore(parameter)) return;

      LocalQuickFix fix = QuickFixFactory.getInstance().createRenameElementFix(parameter, NEW_NAME);
      final LocalQuickFix[] fixes = myIgnore
                                    ? new LocalQuickFix[]{fix}
                                    : LocalQuickFix.EMPTY_ARRAY;
      registerError(catchClause.getFirstChild(), GroovyBundle.message("inspection.message.empty.ref.block"), fixes,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }

    private boolean isEmpty(@NotNull GrOpenBlock body) {
      final GrStatement[] statements = body.getStatements();
      if (statements.length != 0) return false;

      if (myCountCommentsAsContent) {
        final PsiElement brace = body.getLBrace();
        if (brace != null) {
          final PsiElement next = PsiUtil.skipWhitespaces(brace.getNextSibling(), true);
          if (next instanceof PsiComment) {
            return false;
          }
        }
      }

      return true;
    }
  }
}