// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class CommentedOutCodeInspection extends BaseInspection implements CleanupLocalInspectionTool {
  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("inspection.commented.out.code.problem.descriptor", infos[0]);
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new DeleteCommentedOutCodeFix();
  }

  private static class DeleteCommentedOutCodeFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("commented.out.code.delete.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiComment)) {
        return;
      }
      final PsiComment comment = (PsiComment)element;
      if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        final List<PsiElement> toDelete = new ArrayList<>();
        toDelete.add(comment);
        PsiElement sibling = PsiTreeUtil.skipWhitespacesForward(comment);
        while (sibling instanceof PsiComment && ((PsiComment)sibling).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          toDelete.add(sibling);
          sibling = PsiTreeUtil.skipWhitespacesForward(sibling);
        }
        toDelete.forEach(PsiElement::delete);
      }
      else {
        deleteElement(element);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CommentedOutCodeVisitor();
  }

  private static class CommentedOutCodeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitComment(@NotNull PsiComment comment) {
      super.visitComment(comment);
      if (comment instanceof PsiDocComment) {
        return;
      }
      if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        final PsiElement before = PsiTreeUtil.skipWhitespacesBackward(comment);
        if (before instanceof PsiComment && ((PsiComment)before).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          return;
        }
        while (true) {
          final String text = getCommentText(comment);
          if (isCode(text, comment)) {
            registerErrorAtOffset(comment, 0, 2, StringUtil.countNewLines(text) + 1);
            return;
          }
          final PsiElement after = PsiTreeUtil.skipWhitespacesForward(comment);
          if (!(after instanceof PsiComment)) {
            break;
          }
          comment = (PsiComment)after;
          if (comment.getTokenType() != JavaTokenType.END_OF_LINE_COMMENT) {
            break;
          }
        }
      }
      else {
        final String text = getCommentText(comment);
        if (!isCode(text, comment)) {
          return;
        }
        registerErrorAtOffset(comment, 0, 2, StringUtil.countNewLines(text) + 1);
      }
    }

    private static boolean isCode(String text, PsiElement context) {
      if (text.isEmpty()) {
        return false;
      }
      final Project project = context.getProject();
      final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
      final PsiElement fragment;
      final PsiElement parent = context.getParent();
      if (parent instanceof PsiJavaFile) {
        fragment = PsiFileFactory.getInstance(project).createFileFromText("__dummy.java", JavaFileType.INSTANCE, text);
      }
      else if (parent instanceof PsiClass) {
        fragment = factory.createMemberCodeFragment(text, context, false);
      }
      else {
        fragment = factory.createCodeBlockCodeFragment(text, context, false);
      }
      return !isInvalidCode(fragment);
    }

    private static String getCommentText(PsiComment comment) {
      String lineText = getEndOfLineCommentText(comment);
      if (lineText != null) {
        final StringBuilder result = new StringBuilder();
        while (lineText != null) {
          result.append(lineText).append('\n');
          final PsiElement sibling = PsiTreeUtil.skipWhitespacesForward(comment);
          if (!(sibling instanceof PsiComment)) {
            break;
          }
          comment = (PsiComment)sibling;
          lineText = getEndOfLineCommentText(comment);
        }
        return result.toString().trim();
      }
      final String text = comment.getText();
      return StringUtil.trimEnd(StringUtil.trimStart(text, "/*"), "*/").trim();
    }

    @Nullable
    private static String getEndOfLineCommentText(PsiComment comment) {
      return (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) ? StringUtil.trimStart(comment.getText(), "//") : null;
    }
  }

  private static boolean isInvalidCode(PsiElement element) {
    final PsiElement child = element.getFirstChild();
    if (child instanceof PsiComment) {
      final PsiElement lastChild = element.getLastChild();
      if (child == lastChild) {
        return true;
      }
      final PsiElement sibling = child.getNextSibling();
      if (sibling instanceof PsiWhiteSpace && sibling == lastChild) {
        return true;
      }
    }
    final CodeVisitor visitor = new CodeVisitor();
    element.accept(visitor);
    return visitor.isInvalidCode();
  }

  private static class CodeVisitor extends JavaRecursiveElementWalkingVisitor {
    private boolean invalidCode = false;
    private boolean codeFound = false;


    @Override
    public void visitElement(@NotNull PsiElement element) {
      super.visitElement(element);
      if (!(element instanceof PsiFile)) {
        codeFound = true;
      }
    }

    @Override
    public void visitComment(@NotNull PsiComment comment) {}

    @Override
    public void visitWhiteSpace(@NotNull PsiWhiteSpace space) {}

    @Override
    public void visitErrorElement(@NotNull PsiErrorElement element) {
      invalidCode = true;
      stopWalking();
    }

    @Override
    public void visitLiteralExpression(PsiLiteralExpression expression) {
      if (PsiLiteralUtil.isUnsafeLiteral(expression)) {
        invalidCode = true;
        stopWalking();
      }
      else if (expression.getParent() instanceof PsiExpressionStatement) {
        invalidCode = true;
        stopWalking();
      }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression.getParent() instanceof PsiExpressionStatement) {
        invalidCode = true;
        stopWalking();
      }
    }

    @Override
    public void visitLabeledStatement(PsiLabeledStatement statement) {
      super.visitLabeledStatement(statement);
      final String name = statement.getName();
      if (statement.getStatement() == null || name.equals("https") || name.equals("http")) {
        invalidCode = true;
        stopWalking();
      }
    }

    public boolean isInvalidCode() {
      return !codeFound || invalidCode;
    }
  }
}
