// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchHighlightInfoFilter implements HighlightInfoFilter {

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file) {
    if (file == null) {
      return true;
    }
    final Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    if (document == null) {
      return true;
    }
    final String contextId = document.getUserData(StructuralSearchDialog.STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID);
    if (contextId == null) {
      return true;
    }
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(file);
    if (profile == null) {
      return true;
    }
    final PatternContext context = StructuralSearchUtil.findPatternContextByID(contextId, profile);
    return profile.shouldShowProblem(highlightInfo, file, context);
  }
}
