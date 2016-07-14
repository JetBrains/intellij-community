/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class ControlFlowStatementWithoutBracesInspection
  extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "control.flow.statement.without.braces.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "control.flow.statement.without.braces.problem.descriptor", infos);
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length == 1 && infos[0] instanceof String) {
      switch ((String)infos[0]) {
        case PsiKeyword.DO: return new DoBracesFix();
        case PsiKeyword.ELSE: return new ElseBracesFix();
        case PsiKeyword.FOR: return new ForBracesFix();
        case PsiKeyword.IF: return new IfBracesFix();
        case PsiKeyword.WHILE: return new WhileBracesFix();
      }
    }
    return null;
  }

  private static abstract class ControlFlowStatementFix extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "control.flow.statement.without.braces.message", getKeywordText());
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "control.flow.statement.without.braces.add.quickfix");
    }

    abstract String getKeywordText();

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getStartElement();
      final PsiElement parent = element.getParent();
      final PsiStatement statement;
      if (element instanceof PsiStatement) {
        statement = (PsiStatement)element;
      }
      else if ((parent instanceof PsiStatement)) {
        statement = (PsiStatement)parent;
      }
      else {
        return;
      }
      final PsiStatement statementWithoutBraces;
      if (statement instanceof PsiLoopStatement) {
        final PsiLoopStatement loopStatement =
          (PsiLoopStatement)statement;
        statementWithoutBraces = loopStatement.getBody();
      }
      else if (statement instanceof PsiIfStatement) {
        final PsiIfStatement ifStatement = (PsiIfStatement)statement;
        if (element == ifStatement.getElseElement()) {
          statementWithoutBraces = ifStatement.getElseBranch();
        }
        else {
          statementWithoutBraces = ifStatement.getThenBranch();
          if (statementWithoutBraces == null) {
            return;
          }
          final PsiElement nextSibling =
            statementWithoutBraces.getNextSibling();
          if (nextSibling instanceof PsiWhiteSpace) {
            // to avoid "else" on new line
            nextSibling.delete();
          }
        }
      }
      else {
        return;
      }
      if (statementWithoutBraces == null) {
        return;
      }
      final String newStatementText =
        "{\n" + statementWithoutBraces.getText() + "\n}";
      PsiReplacementUtil.replaceStatement(statementWithoutBraces, newStatementText);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ControlFlowStatementVisitor(this);
  }

  private static class ControlFlowStatementVisitor extends ControlFlowStatementVisitorBase {
    private ControlFlowStatementVisitor(BaseInspection inspection) {
      super(inspection);
    }

    @Contract("null->false")
    @Override
    protected boolean isApplicable(PsiStatement body) {
      return body != null && !(body instanceof PsiBlockStatement);
    }
  }

  private static class DoBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return PsiKeyword.DO; } }
  private static class ElseBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return PsiKeyword.ELSE; } }
  private static class ForBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return PsiKeyword.FOR; } }
  private static class IfBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return PsiKeyword.IF; } }
  private static class WhileBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return PsiKeyword.WHILE; } }
}