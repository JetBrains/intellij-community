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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SingleStatementInBlockInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("single.statement.in.block.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("single.statement.in.block.descriptor", infos);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SingleStatementInBlockVisitor(this);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length == 1 && infos[0] instanceof String) {
      switch ((String)infos[0]) {
        case PsiKeyword.DO:
          return new RemoveDoBracesFix();
        case PsiKeyword.ELSE:
          return new RemoveElseBracesFix();
        case PsiKeyword.FOR:
          return new RemoveForBracesFix();
        case PsiKeyword.IF:
          return new RemoveIfBracesFix();
        case PsiKeyword.WHILE:
          return new RemoveWhileBracesFix();
      }
    }
    return null;
  }

  private static void doFixImpl(@NotNull PsiBlockStatement blockStatement) {
    final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
    final PsiStatement[] statements = codeBlock.getStatements();
    final PsiStatement statement = statements[0];

    handleComments(blockStatement, codeBlock);

    final String text = statement.getText();
    PsiReplacementUtil.replaceStatement(blockStatement, text);
  }

  private static void handleComments(PsiBlockStatement blockStatement, PsiCodeBlock codeBlock) {
    final PsiElement parent = blockStatement.getParent();
    assert parent != null;
    final PsiElement grandParent = parent.getParent();
    assert grandParent != null;
    PsiElement sibling = codeBlock.getFirstChild();
    assert sibling != null;
    sibling = sibling.getNextSibling();
    while (sibling != null) {
      if (sibling instanceof PsiComment) {
        grandParent.addBefore(sibling, parent);
      }
      sibling = sibling.getNextSibling();
    }
    final PsiElement lastChild = blockStatement.getLastChild();
    if (lastChild instanceof PsiComment) {
      final PsiElement nextSibling = parent.getNextSibling();
      grandParent.addAfter(lastChild, nextSibling);
    }
  }

  private static class SingleStatementInBlockVisitor extends ControlFlowStatementVisitorBase {
    protected SingleStatementInBlockVisitor(BaseInspection inspection) {
      super(inspection);
    }

    @Contract("null->false")
    @Override
    protected boolean isApplicable(PsiStatement body) {
      if (body instanceof PsiBlockStatement) {
        final PsiBlockStatement statement = (PsiBlockStatement)body;
        final PsiStatement[] statements = statement.getCodeBlock().getStatements();
        if (statements.length == 1 && !(statements[0] instanceof PsiDeclarationStatement)) {
          final PsiFile file = statement.getContainingFile();
          //this inspection doesn't work in JSP files, as it can't tell about tags
          // inside the braces
          if (!FileTypeUtils.isInServerPageFile(file)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  private static abstract class SingleStatementInBlockFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("single.statement.in.block.descriptor", getKeywordText());
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("single.statement.in.block.name");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement startElement = descriptor.getStartElement();
      final PsiElement startParent = startElement.getParent();
      final PsiElement body;
      if (startElement instanceof PsiLoopStatement) {
        body = ((PsiLoopStatement)startElement).getBody();
      }
      else if (startParent instanceof PsiLoopStatement) {
        body = ((PsiLoopStatement)startParent).getBody();
      }
      else {
        assert startElement instanceof PsiKeyword;
        assert startParent instanceof PsiIfStatement;
        PsiIfStatement ifStatement = (PsiIfStatement)startParent;
        body = ((PsiKeyword)startElement).getTokenType() == JavaTokenType.IF_KEYWORD
               ? ifStatement.getThenBranch()
               : ifStatement.getElseBranch();
      }
      assert body instanceof PsiBlockStatement;
      doFixImpl((PsiBlockStatement)body);
    }

    abstract String getKeywordText();
  }

  private static class RemoveDoBracesFix extends SingleStatementInBlockFix { @Override String getKeywordText() { return PsiKeyword.DO; } }
  private static class RemoveElseBracesFix extends SingleStatementInBlockFix { @Override String getKeywordText() { return PsiKeyword.ELSE; } }
  private static class RemoveForBracesFix extends SingleStatementInBlockFix { @Override String getKeywordText() { return PsiKeyword.FOR; } }
  private static class RemoveIfBracesFix extends SingleStatementInBlockFix { @Override String getKeywordText() { return PsiKeyword.IF; } }
  private static class RemoveWhileBracesFix extends SingleStatementInBlockFix { @Override String getKeywordText() { return PsiKeyword.WHILE; } }
}
