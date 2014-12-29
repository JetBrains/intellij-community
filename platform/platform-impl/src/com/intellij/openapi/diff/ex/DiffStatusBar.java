/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.diff.ex;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.ui.components.panels.HorizontalLayout;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Yura Cangea
 */
public class DiffStatusBar extends JPanel {
  private final JLabel myTextLabel = new JLabel("");
  private EditorColorsScheme myColorScheme = null;

  public <T extends LegendTypeDescriptor> DiffStatusBar(List<T> types) {
    super(new HorizontalLayout(10));
    setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
    add(HorizontalLayout.LEFT, myTextLabel);
    for (LegendTypeDescriptor type : types) {
      add(HorizontalLayout.CENTER, new LegendTypeLabel(type));
    }
  }

  public void setText(String text) {
    myTextLabel.setText(text);
  }

  public void setColorScheme(EditorColorsScheme colorScheme) {
    EditorColorsScheme oldScheme = myColorScheme;
    myColorScheme = colorScheme;
    if (oldScheme != colorScheme) repaint();
  }

  public interface LegendTypeDescriptor {
    String getDisplayName();
    @Nullable
    Color getLegendColor(EditorColorsScheme colorScheme);
  }

  private final class LegendTypeLabel extends JLabel implements Icon {
    private final LegendTypeDescriptor myType;

    public LegendTypeLabel(LegendTypeDescriptor type) {
      super(type.getDisplayName(), SwingConstants.LEFT);
      myType = type;
      setIconTextGap(5);
      setIcon(this);
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
      GraphicsUtil.setupAntialiasing(g);
      g.setColor(myType.getLegendColor(myColorScheme != null ? myColorScheme : EditorColorsManager.getInstance().getGlobalScheme()));
      g.fill3DRect(x, y, getIconWidth(), getIconHeight(), true);
    }

    @Override
    public int getIconWidth() {
      return 35;
    }

    @Override
    public int getIconHeight() {
      Font font = getFont();
      return font != null ? font.getSize() - 2 : 10;
    }
  }
}
