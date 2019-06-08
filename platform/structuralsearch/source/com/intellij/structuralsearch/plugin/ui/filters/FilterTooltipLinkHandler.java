// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui.filters;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class FilterTooltipLinkHandler extends TooltipLinkHandler {

  @Override
  public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
    final StructuralSearchDialog dialog = editor.getUserData(StructuralSearchDialog.STRUCTURAL_SEARCH_DIALOG);
    if (dialog != null) {
      dialog.showFilterPanel(refSuffix);
      return true;
    }
    return super.handleLink(refSuffix, editor);
  }
}
