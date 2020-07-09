// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.maturity;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleIntegerFieldOptionsPanel;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
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

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class CommentedOutCodeInspection extends BaseInspection implements CleanupLocalInspectionTool {

  public int minLines = 2;

  @Override
  protected @NotNull @InspectionMessage String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("inspection.commented.out.code.problem.descriptor", infos[0]);
  }

  @Override
  public @Nullable JComponent createOptionsPanel() {
    return new SingleIntegerFieldOptionsPanel(InspectionGadgetsBundle.message("inspection.commented.out.code.min.lines.options"),
                                              this, "minLines");
  }

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    return new InspectionGadgetsFix[] { new DeleteCommentedOutCodeFix(), new UncommentCodeFix() };
  }

  private static class DeleteCommentedOutCodeFix extends InspectionGadgetsFix {

    DeleteCommentedOutCodeFix() {}

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

  private static class UncommentCodeFix extends InspectionGadgetsFix {

    UncommentCodeFix() {}

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("commented.out.code.uncomment.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiComment)) {
        return;
      }
      final PsiComment comment = (PsiComment)element;
      if (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
        final List<TextRange> ranges = new ArrayList<>();
        ranges.add(comment.getTextRange());
        PsiElement sibling = PsiTreeUtil.skipWhitespacesForward(comment);
        while (sibling instanceof PsiComment && ((PsiComment)sibling).getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) {
          ranges.add(sibling.getTextRange());
          sibling = PsiTreeUtil.skipWhitespacesForward(sibling);
        }
        final PsiFile file = element.getContainingFile();
        final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
        assert document != null;
        Collections.reverse(ranges);
        ranges.forEach(r -> document.deleteString(r.getStartOffset(), r.getStartOffset() + 2));
      }
      else {
        final TextRange range = element.getTextRange();
        final PsiFile file = element.getContainingFile();
        final Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
        assert document != null;
        final int start = range.getStartOffset();
        final int end = range.getEndOffset();
        document.deleteString(end - 2, end);
        document.deleteString(start, start + 2);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CommentedOutCodeVisitor();
  }

  private class CommentedOutCodeVisitor extends BaseInspectionVisitor {

    CommentedOutCodeVisitor() {}

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
          final int lines = StringUtil.countNewLines(text) + 1;
          if (lines < minLines) {
            return;
          }
          if (isCode(text, comment)) {
            registerErrorAtOffset(comment, 0, 2, lines);
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
        if (StringUtil.countNewLines(text) + 1 < minLines || !isCode(text, comment)) {
          return;
        }
        registerErrorAtOffset(comment, 0, 2, StringUtil.countNewLines(text) + 1);
      }
    }
  }

  static boolean isCode(String text, PsiElement context) {
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

  static String getCommentText(PsiComment comment) {
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
  static String getEndOfLineCommentText(PsiComment comment) {
    return (comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT) ? StringUtil.trimStart(comment.getText(), "//") : null;
  }

  static boolean isInvalidCode(PsiElement element) {
    final PsiElement firstChild = element.getFirstChild();
    final PsiElement lastChild = element.getLastChild();
    final boolean strict = firstChild == lastChild && firstChild instanceof PsiExpressionStatement;
    if (firstChild instanceof PsiComment) {
      if (firstChild == lastChild) {
        return true;
      }
      final PsiElement sibling = firstChild.getNextSibling();
      if (sibling instanceof PsiWhiteSpace && sibling == lastChild) {
        return true;
      }
    }
    final CodeVisitor visitor = new CodeVisitor(strict);
    element.accept(visitor);
    return visitor.isInvalidCode();
  }

  private static class CodeVisitor extends JavaRecursiveElementWalkingVisitor {
    private final boolean myStrict;
    private boolean invalidCode = false;
    private boolean codeFound = false;

    private CodeVisitor(boolean strict) {
      myStrict = strict;
    }

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

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (myStrict && expression.getParent() instanceof PsiExpressionStatement) {
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if (methodExpression.getQualifierExpression() == null && expression.resolveMethod() == null) {
          invalidCode = true;
          stopWalking();
        }
      }
    }

    public boolean isInvalidCode() {
      return !codeFound || invalidCode;
    }
  }
}
