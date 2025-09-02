// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class StructuralSearchHighlightInfoFilter implements HighlightInfoFilter {

  private static final Key<List<PsiErrorElement>> ERRORS = new Key<>("STRUCTURAL_SEARCH_ERRORS");
  private static final Comparator<PsiErrorElement> ERROR_COMPARATOR =
    Comparator.comparingInt(PsiErrorElement::getTextOffset).thenComparing(PsiErrorElement::getErrorDescription);

  @Override
  public boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile psiFile) {
    if (psiFile == null) {
      return true;
    }
    final Document document = PsiDocumentManager.getInstance(psiFile.getProject()).getDocument(psiFile);
    if (document == null) {
      return true;
    }
    final String contextId = document.getUserData(StructuralSearchDialogKeys.STRUCTURAL_SEARCH_PATTERN_CONTEXT_ID);
    if (contextId == null) {
      return true;
    }
    if (highlightInfo.getSeverity() != HighlightSeverity.ERROR) {
      return false;
    }
    if (!Registry.is("ssr.in.editor.problem.highlighting")) {
      return false;
    }
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(psiFile);
    if (profile == null) {
      return true;
    }
    final PsiErrorElement error = findErrorElementAt(psiFile, highlightInfo.startOffset, highlightInfo.getDescription());
    if (error == null) {
      return false;
    }
    final boolean result = profile.shouldShowProblem(error);
    if (result) {
      final Runnable callback = document.getUserData(StructuralSearchDialogKeys.STRUCTURAL_SEARCH_ERROR_CALLBACK);
      if (callback != null) {
        ApplicationManager.getApplication().invokeLater(callback);
      }
    }
    return result;
  }

  private static PsiErrorElement findErrorElementAt(PsiFile file, int offset, String description) {
    final List<PsiErrorElement> errorList = ERRORS.get(file, findErrors(file));
    for (PsiErrorElement element : errorList) {
      if (element.getTextOffset() == offset && description.equals(element.getErrorDescription())) {
        return element;
      }
    }
    return null;
  }

  private static List<PsiErrorElement> findErrors(PsiFile file) {
    final Collection<PsiErrorElement> errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement.class);
    final List<PsiErrorElement> errorList = new ArrayList<>(errors);
    errorList.sort(ERROR_COMPARATOR);
    file.putUserData(ERRORS, errorList);
    return errorList;
  }
}
