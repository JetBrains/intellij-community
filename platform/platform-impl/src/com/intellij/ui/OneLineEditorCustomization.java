// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public final class OneLineEditorCustomization extends SimpleEditorCustomization {

  public static final OneLineEditorCustomization ENABLED = new OneLineEditorCustomization(true);
  public static final OneLineEditorCustomization DISABLED = new OneLineEditorCustomization(false);

  private OneLineEditorCustomization(boolean enabled) {
    super(enabled);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    editor.setOneLineMode(isEnabled());
  }

}
