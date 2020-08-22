// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
public final class WrapWhenTypingReachesRightMarginCustomization extends SimpleEditorCustomization {

  public static final WrapWhenTypingReachesRightMarginCustomization ENABLED = new WrapWhenTypingReachesRightMarginCustomization(true);
  public static final WrapWhenTypingReachesRightMarginCustomization DISABLED = new WrapWhenTypingReachesRightMarginCustomization(false);

  public static EditorCustomization getInstance(boolean value) {
    return value ? ENABLED : DISABLED;
  }

  private WrapWhenTypingReachesRightMarginCustomization(boolean enabled) {
    super(enabled);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    editor.getSettings().setWrapWhenTypingReachesRightMargin(isEnabled());
  }

}
