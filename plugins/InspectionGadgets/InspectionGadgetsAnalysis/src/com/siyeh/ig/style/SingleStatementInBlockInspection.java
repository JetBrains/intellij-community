/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
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
    return new SingleStatementInBlockVisitor();
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    if (infos.length == 1 && infos[0] instanceof String) {
      return new SingleStatementInBlockFix((String)infos[0]);
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

    @Contract("null->false")
    @Override
    protected boolean isApplicable(PsiStatement body) {
      if (body instanceof PsiBlockStatement) {
        final PsiCodeBlock codeBlock = ((PsiBlockStatement)body).getCodeBlock();
        if (PsiUtilCore.hasErrorElementChild(codeBlock)) {
          return false;
        }
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1 && !(statements[0] instanceof PsiDeclarationStatement) && !isDanglingElseProblem(statements[0], body)) {
          final PsiFile file = body.getContainingFile();
          //this inspection doesn't work in JSP files, as it can't tell about tags
          // inside the braces
          if (!FileTypeUtils.isInServerPageFile(file)) {
            return true;
          }
        }
      }
      return false;
    }

    @Nullable
    @Override
    protected Pair<PsiElement, PsiElement> getOmittedBodyBounds(PsiStatement body) {
      if (body instanceof PsiBlockStatement) {
        final PsiCodeBlock codeBlock = ((PsiBlockStatement)body).getCodeBlock();
        final PsiStatement[] statements = codeBlock.getStatements();
        if (statements.length == 1) {
          final PsiStatement statement = statements[0];
          if (statement instanceof PsiLoopStatement || statement instanceof PsiIfStatement) {
            return Pair.create(codeBlock.getLBrace(), codeBlock.getRBrace());
          }
        }
      }
      return null;
    }

    /**
     * See JLS paragraphs 14.5, 14.9
     */
    private static boolean isDanglingElseProblem(@Nullable PsiStatement statement, @NotNull PsiStatement outerStatement) {
      return hasShortIf(statement) && hasPotentialDanglingElse(outerStatement);
    }

    private static boolean hasShortIf(@Nullable PsiStatement statement) {
      if (statement instanceof PsiIfStatement) {
        final PsiStatement elseBranch = ((PsiIfStatement)statement).getElseBranch();
        return elseBranch == null || hasShortIf(elseBranch);
      }
      if (statement instanceof PsiLabeledStatement) {
        return hasShortIf(((PsiLabeledStatement)statement).getStatement());
      }
      if (statement instanceof PsiWhileStatement || statement instanceof PsiForStatement || statement instanceof PsiForeachStatement) {
        return hasShortIf(((PsiLoopStatement)statement).getBody());
      }
      return false;
    }

    private static boolean hasPotentialDanglingElse(@NotNull PsiStatement statement) {
      final PsiElement parent = statement.getParent();
      if (parent instanceof PsiIfStatement) {
        final PsiIfStatement ifStatement = (PsiIfStatement)parent;
        if (ifStatement.getThenBranch() == statement && ifStatement.getElseBranch() != null) {
          return true;
        }
        return hasPotentialDanglingElse(ifStatement);
      }
      if (parent instanceof PsiLabeledStatement ||
          parent instanceof PsiWhileStatement ||
          parent instanceof PsiForStatement ||
          parent instanceof PsiForeachStatement) {
        return hasPotentialDanglingElse((PsiStatement)parent);
      }
      return false;
    }
  }

  private static class SingleStatementInBlockFix extends InspectionGadgetsFix {
    private final String myKeywordText;

    private SingleStatementInBlockFix(String keywordText) {
      myKeywordText = keywordText;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("single.statement.in.block.quickfix", myKeywordText);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("single.statement.in.block.family.quickfix");
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
      else if (startElement instanceof PsiKeyword) {
        assert startParent instanceof PsiIfStatement;
        PsiIfStatement ifStatement = (PsiIfStatement)startParent;
        body = ((PsiKeyword)startElement).getTokenType() == JavaTokenType.IF_KEYWORD
               ? ifStatement.getThenBranch()
               : ifStatement.getElseBranch();
      }
      else if (PsiUtil.isJavaToken(startElement, JavaTokenType.RBRACE)) { // at the end of the omitted body
        assert startParent instanceof PsiCodeBlock;
        body = startParent.getParent();
      }
      else {
        return;
      }
      assert body instanceof PsiBlockStatement;
      doFixImpl((PsiBlockStatement)body);
    }
  }
}
