/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Contract;

public abstract class ControlFlowStatementVisitorBase extends BaseInspectionVisitor {
  private final HighlightDisplayKey myKey;

  protected ControlFlowStatementVisitorBase(BaseInspection inspection) {
    final String shortName = inspection.getShortName();
    myKey = HighlightDisplayKey.find(shortName);
  }

  @Override
  public void visitForeachStatement(PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    if (isApplicable(statement.getBody())) {
      registerKeywordOrStatementError(statement, PsiKeyword.FOR);
    }
  }


  @Override
  public void visitForStatement(PsiForStatement statement) {
    super.visitForStatement(statement);
    if (isApplicable(statement.getBody())) {
      registerKeywordOrStatementError(statement, PsiKeyword.FOR);
    }
  }

  @Override
  public void visitWhileStatement(PsiWhileStatement statement) {
    super.visitWhileStatement(statement);
    if (isApplicable(statement.getBody())) {
      registerKeywordOrStatementError(statement, PsiKeyword.WHILE);
    }
  }

  @Override
  public void visitDoWhileStatement(PsiDoWhileStatement statement) {
    super.visitDoWhileStatement(statement);
    if (isApplicable(statement.getBody())) {
      registerKeywordOrStatementError(statement, PsiKeyword.DO);
    }
  }

  @Override
  public void visitIfStatement(PsiIfStatement statement) {
    super.visitIfStatement(statement);
    final PsiStatement thenBranch = statement.getThenBranch();
    if (isApplicable(thenBranch)) {
      registerKeywordOrStatementError(statement.getFirstChild(), thenBranch, PsiKeyword.IF);
    }
    final PsiStatement elseBranch = statement.getElseBranch();
    if (isApplicable(elseBranch)) {
      registerKeywordOrStatementError(statement.getElseElement(), elseBranch, PsiKeyword.ELSE);
    }
  }

  @Contract("null->false")
  protected abstract boolean isApplicable(PsiStatement body);

  private void registerKeywordOrStatementError(PsiStatement statement, String text) {
    boolean highlightOnlyKeyword = isHighlightOnlyKeyword(statement);
    if (highlightOnlyKeyword) {
      registerStatementError(statement, text);
    }
    else {
      registerError(statement, text);
    }
  }

  private void registerKeywordOrStatementError(PsiElement keyword, PsiStatement body, String text) {
    boolean highlightOnlyKeyword = isHighlightOnlyKeyword(body);
    if (highlightOnlyKeyword) {
      registerError(keyword != null ? keyword : body, text);
    }
    else {
      registerErrorAtRange(keyword != null ? keyword : body, body, text);
    }
  }

  private boolean isHighlightOnlyKeyword(PsiElement element) {
    if (!isOnTheFly()) {
      return true;
    }
    if (myKey != null) {
      final Project project = element.getProject();
      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
      final HighlightDisplayLevel errorLevel = profile.getErrorLevel(myKey, element);
      return !HighlightDisplayLevel.DO_NOT_SHOW.equals(errorLevel);
    }
    return false;
  }
}
