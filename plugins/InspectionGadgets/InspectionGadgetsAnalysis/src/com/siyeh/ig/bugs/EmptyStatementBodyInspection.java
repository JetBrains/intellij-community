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

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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

  @Override
  @NotNull
  public String getID() {
    return "StatementWithEmptyBody";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("statement.with.empty.body.display.name");
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
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("statement.with.empty.body.include.option"), "m_reportEmptyBlocks");
    panel.addCheckbox(InspectionGadgetsBundle.message("empty.catch.block.comments.option"), "commentsAreContent");
    return panel;
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return !FileTypeUtils.isInServerPageFile(file);
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
      checkLoopStatement(statement);
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
      if (thenBranch != null && isEmpty(thenBranch)) {
        registerStatementError(statement);
        return;
      }
      final PsiStatement elseBranch = statement.getElseBranch();
      if (elseBranch != null && isEmpty(elseBranch)) {
        final PsiElement elseToken = statement.getElseElement();
        if (elseToken == null) {
          return;
        }
        registerError(elseToken);
      }
    }

    @Override
    public void visitSwitchStatement(PsiSwitchStatement statement) {
      super.visitSwitchStatement(statement);
      final PsiCodeBlock body = statement.getBody();
      if (body == null || !isEmpty(body)) {
        return;
      }
      registerStatementError(statement);
    }

    private boolean isEmpty(PsiElement element) {
      if (!commentsAreContent && element instanceof PsiComment) {
        return true;
      }
      else if (element instanceof PsiEmptyStatement) {
        return !commentsAreContent ||
               PsiTreeUtil.getChildOfType(element, PsiComment.class) == null &&
               !(PsiTreeUtil.skipWhitespacesBackward(element) instanceof PsiComment);
      }
      else if (element instanceof PsiWhiteSpace) {
        return true;
      }
      else if (element instanceof PsiBlockStatement) {
        final PsiBlockStatement block = (PsiBlockStatement)element;
        return isEmpty(block.getCodeBlock());
      }
      else if (m_reportEmptyBlocks && element instanceof PsiCodeBlock) {
        final PsiCodeBlock codeBlock = (PsiCodeBlock)element;
        final PsiElement[] children = codeBlock.getChildren();
        if (children.length == 2) {
          return true;
        }
        for (int i = 1; i < children.length - 1; i++) {
          final PsiElement child = children[i];
          if (!isEmpty(child)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }
}