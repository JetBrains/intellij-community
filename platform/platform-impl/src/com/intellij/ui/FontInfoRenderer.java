// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.AntialiasingType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.render.RenderersKt;
import com.intellij.util.ui.FontInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @deprecated This renderer doesn't support rounded selection. Use {@link RenderersKt#fontInfoRenderer(boolean)} instead.
 */
@Deprecated(forRemoval = true)
public class FontInfoRenderer extends ColoredListCellRenderer<Object> {
  @Override
  protected void customizeCellRenderer(@NotNull JList<?> list, Object value, int index, boolean selected, boolean focused) {
    Font font = list.getFont();
    @NlsSafe String text = value == null ? "" : value.toString();
    append(text);
    if (value instanceof FontInfo info) {
      Font f = info.getFont(font.getSize());
      if (f.canDisplayUpTo(text) == -1) {
        setFont(f);
      }
      else {
        append("  " + IdeBundle.message("font.info.renderer.non.latin"), SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
  }

  @Override
  public @NotNull Dimension getPreferredSize() {
    // Existing usages (e.g., FontComboBox) ignore returned preferred width.
    // Calculating preferred width can be quite consuming though (in particular, when a large number of fonts is available),
    // so we avoid such a calculation here.
    return new Dimension(1, computePreferredHeight());
  }

  @Override
  protected void applyAdditionalHints(@NotNull Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(isEditorFont()));
  }

  protected boolean isEditorFont() {
    return false;
  }
}
