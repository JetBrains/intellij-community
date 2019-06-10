// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.text.StringUtil;
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
    if (document == null || document.getUserData(StructuralSearchDialog.STRUCTURAL_SEARCH) == null) {
      return true;
    }
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(file);
    if (profile == null) {
      return true;
    }
    return profile.shouldShowProblem(highlightInfo, file);
  }
}
