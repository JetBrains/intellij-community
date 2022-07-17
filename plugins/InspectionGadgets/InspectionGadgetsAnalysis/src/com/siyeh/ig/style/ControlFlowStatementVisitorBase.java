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

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ControlFlowStatementVisitorBase extends BaseInspectionVisitor {

  @Override
  public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
    super.visitForeachStatement(statement);
    final PsiStatement body = statement.getBody();
    if (isApplicable(body)) {
      registerLoopStatementErrors(statement, body, PsiKeyword.FOR);
    }
  }

  @Override
  public void visitForStatement(@NotNull PsiForStatement statement) {
    super.visitForStatement(statement);
    final PsiStatement body = statement.getBody();
    if (isApplicable(body)) {
      registerLoopStatementErrors(statement, body, PsiKeyword.FOR);
    }
  }


  @Override
  public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
    super.visitWhileStatement(statement);
    final PsiStatement body = statement.getBody();
    if (isApplicable(body)) {
      registerLoopStatementErrors(statement, body, PsiKeyword.WHILE);
    }
  }

  @Override
  public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
    super.visitDoWhileStatement(statement);
    final PsiStatement body = statement.getBody();
    if (isApplicable(body)) {
      registerLoopStatementErrors(statement, body, PsiKeyword.DO);
    }
  }

  @Override
  public void visitIfStatement(@NotNull PsiIfStatement statement) {
    super.visitIfStatement(statement);
    final PsiStatement thenBranch = statement.getThenBranch();
    if (isApplicable(thenBranch)) {
      registerControlFlowStatementErrors(statement.getFirstChild(), thenBranch.getLastChild(), thenBranch, PsiKeyword.IF);
    }
    final PsiStatement elseBranch = statement.getElseBranch();
    if (isApplicable(elseBranch)) {
      registerControlFlowStatementErrors(statement.getElseElement(), elseBranch.getLastChild(), elseBranch, PsiKeyword.ELSE);
    }
  }

  @Contract("null->false")
  protected abstract boolean isApplicable(PsiStatement body);

  @Nullable
  protected abstract Pair<PsiElement, PsiElement> getOmittedBodyBounds(PsiStatement body);

  private void registerLoopStatementErrors(@NotNull PsiLoopStatement statement, @NotNull PsiStatement body, @NotNull String keywordText) {
    registerControlFlowStatementErrors(statement.getFirstChild(), statement.getLastChild(), body, keywordText);
  }

  private void registerControlFlowStatementErrors(@Nullable PsiElement rangeStart,
                                                  @Nullable PsiElement rangeEnd,
                                                  @NotNull PsiStatement body,
                                                  @NotNull String keywordText) {
    boolean highlightOnlyKeyword = isVisibleHighlight(body);
    if (highlightOnlyKeyword) {
      if (rangeStart != null) {
        registerError(rangeStart, keywordText);
      }
      return;
    }

    final Pair<PsiElement, PsiElement> omittedBodyBounds = getOmittedBodyBounds(body);
    if (omittedBodyBounds == null) {
      if (rangeStart != null && rangeEnd != null) {
        registerErrorAtRange(rangeStart, rangeEnd, keywordText);
      }
      return;
    }

    if (rangeStart != null) {
      PsiElement parent = PsiTreeUtil.findCommonParent(rangeStart, omittedBodyBounds.getFirst());
      if (parent != null) {
        int parentStart = parent.getTextRange().getStartOffset();
        int startOffset = rangeStart.getTextRange().getStartOffset();
        int length = omittedBodyBounds.getFirst().getTextRange().getStartOffset() - startOffset;
        if (length > 0) {
          registerErrorAtOffset(parent, startOffset - parentStart, length, keywordText);
        }
      }
    }

    final PsiElement afterOmitted = omittedBodyBounds.getSecond();
    if (afterOmitted != null) {
      if (rangeEnd != null && rangeEnd != afterOmitted) {
        PsiElement parent = PsiTreeUtil.findCommonParent(rangeEnd, afterOmitted);
        if (parent != null) {
          int parentStart = parent.getTextRange().getStartOffset();
          int startOffset = afterOmitted.getTextRange().getEndOffset();
          int length = rangeEnd.getTextRange().getEndOffset() - startOffset;
          if (length > 0) {
            registerErrorAtOffset(parent, startOffset - parentStart, length,
                                  keywordText);
          }
        }
      }
    }
  }
}