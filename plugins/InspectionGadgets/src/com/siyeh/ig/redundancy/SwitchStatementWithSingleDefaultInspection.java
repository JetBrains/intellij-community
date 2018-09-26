/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.redundancy;

import com.intellij.codeInsight.BlockUtils;
import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.BreakConverter;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class SwitchStatementWithSingleDefaultInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitSwitchStatement(PsiSwitchStatement statement) {
        PsiCodeBlock body = statement.getBody();
        if (body == null) return;
        PsiElement anchor = Objects.requireNonNull(statement.getFirstChild());
        PsiStatement[] statements = body.getStatements();
        if (statements.length == 0) return;
        if (!(statements[0] instanceof PsiSwitchLabelStatement) || !((PsiSwitchLabelStatement)statements[0]).isDefaultCase()) return;
        if (StreamEx.of(statements).skip(1).anyMatch(PsiSwitchLabelStatement.class::isInstance)) return;
        if (BreakConverter.from(statement) == null) return;
        holder.registerProblem(anchor, InspectionsBundle.message("inspection.switch.statement.with.single.default.message"),
                               new UnwrapSwitchStatementFix());
      }
    };
  }

  private static class UnwrapSwitchStatementFix implements LocalQuickFix {
    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.unwrap.statement", PsiKeyword.SWITCH);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiSwitchStatement statement = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiSwitchStatement.class);
      if (statement == null) return;
      PsiCodeBlock body = statement.getBody();
      if (body == null) return;
      BreakConverter breakConverter = BreakConverter.from(statement);
      if (breakConverter == null) return;
      breakConverter.process(true);
      PsiSwitchLabelStatement defaultCase = PsiTreeUtil.getChildOfType(body, PsiSwitchLabelStatement.class);
      if (defaultCase == null || !defaultCase.isDefaultCase()) return;
      defaultCase.delete();
      PsiElement parent = statement.getParent();
      if (!(parent instanceof PsiCodeBlock) || !BlockUtils.containsConflictingDeclarations(body, (PsiCodeBlock)parent)) {
        BlockUtils.inlineCodeBlock(statement, body);
      }
      else {
        PsiBlockStatement blockStatement = BlockUtils.createBlockStatement(project);
        blockStatement.getCodeBlock().replace(body);
        statement.replace(blockStatement);
      }
    }
  }
}