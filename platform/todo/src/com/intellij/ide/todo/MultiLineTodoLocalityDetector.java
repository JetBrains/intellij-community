// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class MultiLineTodoLocalityDetector implements ChangeLocalityDetector {
  @Override
  public @Nullable PsiElement getChangeHighlightingDirtyScopeFor(@NotNull PsiElement changedElement) {
    if (!TodoConfiguration.getInstance().isMultiLine()) return null;
    if (changedElement instanceof PsiWhiteSpace) {
      PsiElement prevLeaf = PsiTreeUtil.prevLeaf(changedElement);
      return prevLeaf instanceof PsiComment ? PsiTreeUtil.findCommonParent(changedElement, prevLeaf) : null;
    }
    else if (changedElement instanceof PsiComment) {
      PsiComment commentAbove = findAdjacentComment(changedElement, true);
      PsiComment commentBelow = findAdjacentComment(changedElement, false);
      if (commentAbove == null && commentBelow == null) return null;
      return PsiTreeUtil.findCommonParent(changedElement, commentAbove, commentBelow);
    }
    else {
      return null;
    }
  }

  private static PsiComment findAdjacentComment(PsiElement element, boolean above) {
    PsiElement currentElement = element;
    PsiComment lastComment = null;
    boolean nextLine = false;
    while (true) {
      currentElement = above ? PsiTreeUtil.prevLeaf(currentElement) : PsiTreeUtil.nextLeaf(currentElement);
      if (currentElement == null) break;
      String elementText = currentElement.getText();
      if (elementText == null) break;
      int newLines = StringUtil.countNewLines(elementText);
      if (newLines == 0 && !nextLine) continue;
      if (currentElement instanceof PsiComment) lastComment = (PsiComment)currentElement;
      if (newLines > (nextLine ? 0 : 1)) break;
      nextLine = true;
    }
    return lastComment;
  }
}
