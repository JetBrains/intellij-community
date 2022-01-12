// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.plugin.ui.modifier;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.structuralsearch.plugin.ui.StructuralSearchDialog;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class ModifierTooltipLinkHandler extends TooltipLinkHandler {

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
