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
package com.intellij.android.designer.propertyTable.renderers;

import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.renderers.BooleanRenderer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class ResourceRenderer implements PropertyRenderer {
  private static final String[] DIMENSIONS = {"dp", "sp", "pt", "px", "mm", "in"};

  private final ColorIcon myColorIcon = new ColorIcon(10, 9);
  private BooleanRenderer myBooleanRenderer;
  private final SimpleColoredComponent myColoredComponent;
  private final Set<AttributeFormat> myFormats;

  public ResourceRenderer(Set<AttributeFormat> formats) {
    if (formats.contains(AttributeFormat.Boolean)) {
      myBooleanRenderer = new BooleanRenderer();
    }

    myColoredComponent = new SimpleColoredComponent();

    myFormats = formats;
  }

  @NotNull
  @Override
  public JComponent getComponent(@Nullable RadComponent component, @Nullable Object object, boolean selected, boolean hasFocus) {
    String value = (String)object;

    if (myBooleanRenderer != null && (StringUtil.isEmpty(value) || "false".equals(value) || "true".equals(value))) {
      return myBooleanRenderer.getComponent(component, "true".equals(value), selected, hasFocus);
    }

    myColoredComponent.clear();

    if (selected) {
      myColoredComponent.setForeground(UIUtil.getTableSelectionForeground());
      myColoredComponent.setBackground(UIUtil.getTableSelectionBackground());
    }
    else {
      myColoredComponent.setForeground(UIUtil.getTableForeground());
      myColoredComponent.setBackground(UIUtil.getTableBackground());
    }

    if (!StringUtil.isEmpty(value)) {
      if (value.charAt(0) == '@') {
        myColoredComponent.append("@", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        myColoredComponent.append(value.substring(1));
      }
      else if (myFormats.contains(AttributeFormat.Dimension) && value.length() > 2) {
        int index = value.length() - 2;
        String dimension = value.substring(index);
        if (ArrayUtil.indexOf(DIMENSIONS, dimension) != -1) {
          myColoredComponent.append(value.substring(0, index));
          myColoredComponent.append(dimension, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
        else {
          myColoredComponent.append(value);
        }
      }
      else {
        myColoredComponent.append(value);
      }
      if (myFormats.contains(AttributeFormat.Color) && value.charAt(0) == '#') {
        try {
          myColorIcon.setColor(new Color(Integer.parseInt(value.substring(1), 16)));
          myColoredComponent.setIcon(myColorIcon);
        }
        catch (Throwable e) {
        }
      }
    }

    return myColoredComponent;
  }

  @Override
  public void updateUI() {
    if (myBooleanRenderer != null) {
      SwingUtilities.updateComponentTreeUI(myBooleanRenderer);
    }
    SwingUtilities.updateComponentTreeUI(myColoredComponent);
  }

  private static class ColorIcon extends EmptyIcon {
    private final int myColorSize;
    private Color myColor;

    public ColorIcon(int size, int colorSize) {
      super(size, size);
      myColorSize = colorSize;
    }

    public void setColor(Color color) {
      myColor = color;
    }

    @Override
    public void paintIcon(Component component, Graphics g, final int i, final int j) {
      int iconWidth = getIconWidth();
      int iconHeight = getIconHeight();
      g.setColor(myColor);

      int x = i + (iconWidth - myColorSize) / 2;
      int y = j + (iconHeight - myColorSize) / 2;

      g.fillRect(x, y, myColorSize, myColorSize);
      g.setColor(Color.BLACK);
      g.drawRect(x, y, myColorSize, myColorSize);
    }
  }
}