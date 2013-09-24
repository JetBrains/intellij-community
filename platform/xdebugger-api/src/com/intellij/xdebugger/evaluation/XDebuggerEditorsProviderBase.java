package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class XDebuggerEditorsProviderBase extends XDebuggerEditorsProvider {
  @NotNull
  @Override
  public final Document createDocument(@NotNull Project project, @NotNull String text, @Nullable XSourcePosition sourcePosition, @NotNull EvaluationMode mode) {
    PsiElement context = null;
    if (sourcePosition != null) {
      context = getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project);
    }

    PsiFile codeFragment = createExpressionCodeFragment(project, text, context, true);
    Document document = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
    assert document != null;
    return document;
  }

  protected abstract PsiFile createExpressionCodeFragment(@NotNull Project project, @NotNull String text, @Nullable PsiElement context, boolean isPhysical);

  protected PsiElement getContextElement(@NotNull VirtualFile virtualFile, int offset, @NotNull Project project) {
    return doGetContextElement(virtualFile, offset, project);
  }

  public static PsiElement doGetContextElement(@NotNull VirtualFile virtualFile, int offset, @NotNull Project project) {
    Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
    if (file == null || document == null) {
      return null;
    }

    if (offset < 0) {
      offset = 0;
    }
    if (offset > document.getTextLength()) {
      offset = document.getTextLength();
    }
    int startOffset = offset;

    int lineEndOffset = document.getLineEndOffset(document.getLineNumber(offset));
    PsiElement result = null;
    do {
      PsiElement element = file.findElementAt(offset);
      if (!(element instanceof PsiWhiteSpace) && !(element instanceof PsiComment)) {
        result = element;
        break;
      }

      offset = element.getTextRange().getEndOffset() + 1;
    }
    while (offset < lineEndOffset);

    if (result == null) {
      result = file.findElementAt(startOffset);
    }
    return result;
  }
}