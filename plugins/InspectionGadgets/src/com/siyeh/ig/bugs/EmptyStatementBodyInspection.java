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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.impl.quickfix.DeleteElementFix;
import com.intellij.codeInsight.daemon.impl.quickfix.DeleteSideEffectsAwareFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.DelegatingFix;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class EmptyStatementBodyInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_reportEmptyBlocks = true;

  @SuppressWarnings("PublicField")
  public boolean commentsAreContent = false;

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    node.addContent(new Element("option").setAttribute("name", "m_reportEmptyBlocks").setAttribute("value", String.valueOf(m_reportEmptyBlocks)));
    if (commentsAreContent) {
      node.addContent(new Element("option").setAttribute("name", "commentsAreContent").setAttribute("value", "true"));
    }
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "StatementWithEmptyBody";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("statement.with.empty.body.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_reportEmptyBlocks", InspectionGadgetsBundle.message("statement.with.empty.body.include.option")),
      checkbox("commentsAreContent", InspectionGadgetsBundle.message("comments.as.content.option")));
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    LocalQuickFix fix = ObjectUtils.tryCast(ArrayUtil.getFirstElement(infos), LocalQuickFix.class);
    return fix == null ? null : new DelegatingFix(fix);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EmptyStatementVisitor();
  }

  private class EmptyStatementVisitor extends BaseInspectionVisitor {

    @Override
    public void visitDoWhileStatement(@NotNull PsiDoWhileStatement statement) {
      super.visitDoWhileStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitForStatement(@NotNull PsiForStatement statement) {
      super.visitForStatement(statement);
      checkLoopStatement(statement);
    }

    @Override
    public void visitForeachStatement(@NotNull PsiForeachStatement statement) {
      super.visitForeachStatement(statement);
      final PsiStatement body = statement.getBody();
      if (body != null && isEmpty(body)) {
        registerStatementError(statement, createFix(statement, statement.getIteratedValue()));
      }
    }

    private void checkLoopStatement(PsiLoopStatement statement) {
      final PsiStatement body = statement.getBody();
      if (body == null || !isEmpty(body)) {
        return;
      }
      registerStatementError(statement);
    }

    @Override
    public void visitIfStatement(@NotNull PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiStatement thenBranch = statement.getThenBranch();
      final PsiStatement elseBranch = statement.getElseBranch();
      if (thenBranch != null && isEmpty(thenBranch)) {
        LocalQuickFix fix = elseBranch == null || isEmpty(elseBranch) ? createFix(statement, statement.getCondition()) : null;
        registerStatementError(statement, fix);
        return;
      }
      if (elseBranch != null && isEmpty(elseBranch)) {
        final PsiElement elseToken = statement.getElseElement();
        if (elseToken == null) {
          return;
        }
        registerError(elseToken, new DeleteElementFix(elseBranch));
      }
    }

    @Override
    public void visitSwitchStatement(@NotNull PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      final PsiCodeBlock body = statement.getBody();
      if (body == null || !isEmpty(body)) {
        return;
      }
      registerStatementError(statement, createFix(statement, statement.getExpression()));
    }

    @NotNull
    private LocalQuickFix createFix(@NotNull PsiStatement statement, PsiExpression expression) {
      if (expression == null) {
        return new DeleteElementFix(statement);
      }
      return new DeleteSideEffectsAwareFix(statement, expression);
    }

    private boolean isEmpty(PsiElement element) {
      return ControlFlowUtils.isEmpty(element, commentsAreContent, m_reportEmptyBlocks);
    }
  }
}