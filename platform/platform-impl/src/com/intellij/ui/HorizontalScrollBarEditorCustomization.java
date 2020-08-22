// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author irengrig
 */
public final class HorizontalScrollBarEditorCustomization extends SimpleEditorCustomization {

  public static final HorizontalScrollBarEditorCustomization ENABLED = new HorizontalScrollBarEditorCustomization(true);
  public static final HorizontalScrollBarEditorCustomization DISABLED = new HorizontalScrollBarEditorCustomization(false);

  private HorizontalScrollBarEditorCustomization(boolean enabled) {
    super(enabled);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    editor.setHorizontalScrollbarVisible(isEnabled());
  }
}
