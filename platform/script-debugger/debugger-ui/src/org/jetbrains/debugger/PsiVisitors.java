// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ForeignLeafPsiElement;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.util.DocumentUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated scriptDebugger.ui is deprecated
 */
@Deprecated
public final class PsiVisitors {

  @ApiStatus.Internal
  @RequiresReadLock
  public static <RESULT> RESULT visit(@NotNull XSourcePosition position,
                                      @NotNull Project project,
                                      @Nullable RESULT defaultResult,
                                      @NotNull Visitor<? extends RESULT> visitor) {
    Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
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
    return element == null ? defaultResult : visitor.visit(position, element, positionOffset, document);
  }

  @ApiStatus.Internal
  public interface Visitor<RESULT> {
    RESULT visit(@NotNull XSourcePosition position, @NotNull PsiElement element, int positionOffset, @NotNull Document document);
  }

  /**
   * @deprecated Use a specific visitor suitable for your task, the purpose of this one is unclear.
   */
  @Deprecated
  public abstract static class FilteringPsiRecursiveElementWalkingVisitor extends PsiRecursiveElementWalkingVisitor {
    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!(element instanceof ForeignLeafPsiElement) && element.isPhysical()) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitWhiteSpace(@NotNull PsiWhiteSpace space) {
    }

    @Override
    public void visitComment(@NotNull PsiComment comment) {
    }

    @Override
    public void visitOuterLanguageElement(@NotNull OuterLanguageElement element) {
    }
  }
}