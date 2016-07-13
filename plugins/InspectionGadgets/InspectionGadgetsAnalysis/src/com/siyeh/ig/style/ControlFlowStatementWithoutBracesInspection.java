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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ControlFlowStatementWithoutBracesInspection
  extends BaseInspection {

  @NonNls private static final String DO_TEXT = "do";
  @NonNls private static final String ELSE_TEXT = "else";
  @NonNls private static final String FOR_TEXT = "for";
  @NonNls private static final String IF_TEXT = "if";
  @NonNls private static final String WHILE_TEXT = "while";

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
        case DO_TEXT: return new DoBracesFix();
        case ELSE_TEXT: return new ElseBracesFix();
        case FOR_TEXT: return new ForBracesFix();
        case IF_TEXT: return new IfBracesFix();
        case WHILE_TEXT: return new WhileBracesFix();
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
    final String shortName = getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);
    return new ControlFlowStatementVisitor(key);
  }

  private static class ControlFlowStatementVisitor
    extends BaseInspectionVisitor {
    private HighlightDisplayKey myKey;

    public ControlFlowStatementVisitor(HighlightDisplayKey key) {
      myKey = key;
    }

    @Override
    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      final PsiStatement body = statement.getBody();
      if (body == null || body instanceof PsiBlockStatement) {
        return;
      }
      registerKeywordOrStatementError(statement, DO_TEXT);
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      final PsiStatement body = statement.getBody();
      if (body == null || body instanceof PsiBlockStatement) {
        return;
      }
      registerKeywordOrStatementError(statement, FOR_TEXT);
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
      super.visitForStatement(statement);
      final PsiStatement body = statement.getBody();
      if (body == null || body instanceof PsiBlockStatement) {
        return;
      }
      registerKeywordOrStatementError(statement, FOR_TEXT);
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiStatement thenBranch = statement.getThenBranch();
      if (thenBranch == null) {
        return;
      }
      boolean highlightOnlyKeyword = isHighlightOnlyKeyword(statement);
      if (!(thenBranch instanceof PsiBlockStatement)) {
        if (highlightOnlyKeyword) {
          registerStatementError(statement, IF_TEXT);
        }
        else {
          final PsiElement startElement = statement.getFirstChild();
          registerErrorAtRange(startElement != null ? startElement : thenBranch, thenBranch, IF_TEXT);
        }
      }
      final PsiStatement elseBranch = statement.getElseBranch();
      if (elseBranch == null) {
        return;
      }
      if (!(elseBranch instanceof PsiBlockStatement) &&
          !(elseBranch instanceof PsiIfStatement)) {
        final PsiKeyword elseKeyword = statement.getElseElement();
        if (elseKeyword == null) {
          return;
        }
        if (highlightOnlyKeyword) {
          registerError(elseKeyword, ELSE_TEXT);
        }
        else {
          registerErrorAtRange(elseKeyword, elseBranch, ELSE_TEXT);
        }
      }
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      final PsiStatement body = statement.getBody();
      if (body == null || body instanceof PsiBlockStatement) {
        return;
      }
      registerKeywordOrStatementError(statement, WHILE_TEXT);
    }

    private void registerKeywordOrStatementError(PsiStatement statement, String text) {
      boolean highlightOnlyKeyword = isHighlightOnlyKeyword(statement);
      if (highlightOnlyKeyword) {
        registerStatementError(statement, text);
      }
      else {
        registerError(statement, text);
      }
    }

    private boolean isHighlightOnlyKeyword(PsiElement element) {
      if (!isOnTheFly()) {
        return true;
      }
      if (myKey != null) {
        final Project project = element.getProject();
        final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
        HighlightDisplayLevel errorLevel = profile.getErrorLevel(myKey, element);
        return !HighlightDisplayLevel.DO_NOT_SHOW.equals(errorLevel);
      }
      return false;
    }
  }

  private static class DoBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return DO_TEXT; } }
  private static class ElseBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return ELSE_TEXT; } }
  private static class ForBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return FOR_TEXT; } }
  private static class IfBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return IF_TEXT; } }
  private static class WhileBracesFix extends ControlFlowStatementFix { @Override String getKeywordText() { return WHILE_TEXT; } }
}