/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.util.ui.FontInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Sergey.Malenkov
 */
public class FontInfoRenderer extends ColoredListCellRenderer<Object> {
  @Override
  protected void customizeCellRenderer(@NotNull JList<?> list, Object value, int index, boolean selected, boolean focused) {
    Font font = list.getFont();
    String text = value == null ? "" : value.toString();
    append(text);
    if (value instanceof FontInfo) {
      FontInfo info = (FontInfo)value;
      Integer size = getFontSize();
      Font f = info.getFont(size != null ? size : font.getSize());
      if (f.canDisplayUpTo(text) == -1) {
        setFont(f);
      }
      else {
        append("  Non-latin", SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    }
  }

  @NotNull
  @Override
  public Dimension getPreferredSize() {
    // Existing usages (e.g. FontComboBox) ignore returned preferred width. 
    // Calculating preferred width can be quite consuming though (in particular, when a large number of fonts is available),
    // so we avoid such a calculation here.
    return new Dimension(1, computePreferredHeight());
  }

  @Override
  protected void applyAdditionalHints(@NotNull Graphics2D g) {
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(isEditorFont()));
  }

  protected Integer getFontSize() {
    return null;
  }

  protected boolean isEditorFont() {
    return false;
  }
}
