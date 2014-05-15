package org.jetbrains.debugger;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.util.DocumentUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;

public final class PsiVisitors {
  /**
   * Read action will be taken automatically
   */
  public static <RESULT> RESULT visit(@NotNull XSourcePosition position, @NotNull Project project, @NotNull Visitor<RESULT> visitor, RESULT defaultResult) {
    Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
    AccessToken token = ReadAction.start();
    try {
      PsiFile file = document == null || document.getTextLength() == 0 ? null : PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (file == null) {
        return defaultResult;
      }

      int positionOffset;
      int column = position instanceof SourceInfo ? Math.max(((SourceInfo)position).getColumn(), 0) : 0;
      try {
        positionOffset = column == 0 ? DocumentUtil.getFirstNonSpaceCharOffset(document, position.getLine()) : document.getLineStartOffset(position.getLine()) + column;
      }
      catch (IndexOutOfBoundsException ignored) {
        return defaultResult;
      }

      PsiElement element = file.findElementAt(positionOffset);
      return element == null ? defaultResult : visitor.visit(element, positionOffset, document);
    }
    finally {
      token.finish();
    }
  }

  public static abstract class Visitor<RESULT> {
    public abstract RESULT visit(@NotNull PsiElement element, int positionOffset, @NotNull Document document);
  }

  public static abstract class FilteringPsiRecursiveElementWalkingVisitor extends PsiRecursiveElementWalkingVisitor {
    @Override
    public void visitElement(PsiElement element) {
      if (!(element instanceof ForeignLeafPsiElement) && element.isPhysical()) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitWhiteSpace(PsiWhiteSpace space) {
    }

    @Override
    public void visitComment(PsiComment comment) {
    }

    @Override
    public void visitOuterLanguageElement(OuterLanguageElement element) {
    }
  }
}