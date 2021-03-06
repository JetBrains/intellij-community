// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public final class AdditionalPageAtBottomEditorCustomization extends SimpleEditorCustomization {

  public static final AdditionalPageAtBottomEditorCustomization ENABLED = new AdditionalPageAtBottomEditorCustomization(true);
  public static final AdditionalPageAtBottomEditorCustomization DISABLED = new AdditionalPageAtBottomEditorCustomization(false);

  private AdditionalPageAtBottomEditorCustomization(boolean enabled) {
    super(enabled);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    editor.getSettings().setAdditionalPageAtBottom(isEnabled());
  }
}
