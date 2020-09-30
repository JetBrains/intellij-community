// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

/**
 * Editor customization that can make target editor soft wraps-aware.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 */
public final class SoftWrapsEditorCustomization extends SimpleEditorCustomization {

  public static final SoftWrapsEditorCustomization ENABLED = new SoftWrapsEditorCustomization(true);
  public static final SoftWrapsEditorCustomization DISABLED = new SoftWrapsEditorCustomization(false);

  private SoftWrapsEditorCustomization(boolean enabled) {
    super(enabled);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    editor.getSettings().setUseSoftWraps(isEnabled());
  }
}
