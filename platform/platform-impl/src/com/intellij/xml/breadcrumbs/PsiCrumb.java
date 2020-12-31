// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.breadcrumbs;

import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiAnchor;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.ui.components.breadcrumbs.Crumb;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PsiCrumb extends Crumb.Impl implements NavigatableCrumb, LazyTooltipCrumb {
  private final PsiAnchor anchor;
  private volatile BreadcrumbsProvider provider;
  private volatile @NlsContexts.Tooltip String tooltip;
  final CrumbPresentation presentation;

  PsiCrumb(@NotNull PsiElement element, @NotNull BreadcrumbsProvider provider, @Nullable CrumbPresentation presentation) {
    super(provider.getElementIcon(element), provider.getElementInfo(element), null, provider.getContextActions(element));
    anchor = PsiAnchor.create(element);
    this.provider = provider;
    this.presentation = presentation;
  }

  @Override
  public String getTooltip() {
    if (needCalculateTooltip()) {
      PsiElement element = getElement(this);
      tooltip = element == null ? null
                                : provider.getElementTooltip(element);
      provider = null; // do not try recalculate tooltip
      if (element != null) {
        UIEventLogger.BreadcrumbShowTooltip.log(element.getProject(), element.getLanguage());
      }
    }
    return tooltip;
  }

  @Override
  public boolean needCalculateTooltip() {
    return provider != null && tooltip == null;
  }

  @Override
  public int getAnchorOffset() {
    PsiElement element = anchor.retrieve();
    return element != null ? element.getTextOffset() : -1;
  }

  @Nullable
  @Override
  public TextRange getHighlightRange() {
    PsiElement element = anchor.retrieve();
    return element != null ? element.getTextRange() : null;
  }

  @Override
  public void navigate(@NotNull Editor editor, boolean withSelection) {
    int offset = getAnchorOffset();
    if (offset != -1) {
      moveEditorCaretTo(editor, offset);
    }

    PsiElement element = getElement(this);
    if (withSelection) {
      final TextRange range = getHighlightRange();
      if (range != null) {
        editor.getSelectionModel().setSelection(range.getStartOffset(), range.getEndOffset());
      }
    }
    UIEventLogger.BreadcrumbNavigate.log(element != null ? element.getProject() : null, element != null ? element.getLanguage() : null, withSelection);
  }

  private static void moveEditorCaretTo(Editor editor, int offset) {
    if (offset >= 0) {
      editor.getCaretModel().moveToOffset(offset);
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  @Contract("null -> null")
  static PsiElement getElement(Crumb crumb) {
    return crumb instanceof PsiCrumb ? ((PsiCrumb)crumb).anchor.retrieve() : null;
  }

  @Contract(value = "null -> null", pure = true)
  static CrumbPresentation getPresentation(Crumb crumb) {
    return crumb instanceof PsiCrumb ? ((PsiCrumb)crumb).presentation : null;
  }
}
