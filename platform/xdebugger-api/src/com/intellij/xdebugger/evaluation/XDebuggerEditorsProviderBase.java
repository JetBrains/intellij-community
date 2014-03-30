package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XDebuggerUtil;
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

  @Nullable
  protected PsiElement getContextElement(@NotNull VirtualFile virtualFile, int offset, @NotNull Project project) {
    return XDebuggerUtil.getInstance().findContextElement(virtualFile, offset, project, false);
  }
}