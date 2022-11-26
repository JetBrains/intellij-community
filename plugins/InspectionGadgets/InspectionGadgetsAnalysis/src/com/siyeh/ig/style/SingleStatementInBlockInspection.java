// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.*;

public class SingleStatementInBlockInspection extends BaseInspection implements CleanupLocalInspectionTool {

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
          if (PsiUtilCore.hasErrorElementChild(statements[0])) {
            return false;
          }
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
          if (statement.textContains('\n')) {
            return Pair.create(statement, statement);
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
    private final @NonNls String myKeywordText;

    SingleStatementInBlockFix(String keywordText) {
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
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiStatement statement = PsiTreeUtil.getNonStrictParentOfType(descriptor.getStartElement(), PsiStatement.class);
      if (statement instanceof PsiBlockStatement) {
        statement = PsiTreeUtil.getNonStrictParentOfType(statement.getParent(), PsiStatement.class);
      }
      final PsiElement body;
      if (statement instanceof PsiLoopStatement) {
        body = ((PsiLoopStatement)statement).getBody();
      }
      else if (statement instanceof PsiIfStatement) {
        body = myKeywordText.equals("else") ? ((PsiIfStatement)statement).getElseBranch() : ((PsiIfStatement)statement).getThenBranch();
      }
      else {
        return;
      }
      if (!(body instanceof PsiBlockStatement)) return;
      final PsiStatement[] statements = ((PsiBlockStatement)body).getCodeBlock().getStatements();
      if (statements.length != 1) return;

      CommentTracker commentTracker = new CommentTracker();
      final String text = commentTracker.text(statements[0]);
      final PsiElement replacementExp = commentTracker.replace(body, text);
      CodeStyleManager.getInstance(project).reformat(replacementExp);
      commentTracker.insertCommentsBefore(statement);
    }
  }
}
