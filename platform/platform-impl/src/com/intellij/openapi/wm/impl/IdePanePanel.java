/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class IdePanePanel extends JBPanel {
  public IdePanePanel(LayoutManager layout) {
    super(layout);
  }

  @Nullable
  @Override
  public Icon getCenterImage() {
    return getIdeBackgroundLogo();
  }

  @Nullable
  @Override
  public Icon getBackgroundImage() {
    return getIdeBackgroundImage();
  }

  @Override
  public Color getBackground() {
    return getIdeBackgroundColor();
  }

  @Nullable
  public static Icon getIdeBackgroundImage() {
    return null;
  }

  @Nullable
  public static Icon getIdeBackgroundLogo() {
    if (UIUtil.isUnderDarcula()) {
      String url = ApplicationInfoEx.getInstanceEx().getEditorBackgroundImageUrl();
      if (url != null) {
        return IconLoader.getIcon(url);
      }
    }
    return null;
  }

  @Override
  protected void paintCenterImage(Graphics g) {
    final Icon image = getCenterImage();
    if (image != null) {
      final int x = (getWidth() - image.getIconWidth()) / 2;
      final int y = ((getHeight() - 222) / 2 - image.getIconHeight()) / 2;
      image.paintIcon(this, g, x, y);
    }
  }

  @Nullable
  public static Color getIdeBackgroundColor() {
    Color result = UIUtil.getSlightlyDarkerColor(UIUtil.getPanelBackground());
    return UIUtil.isUnderDarcula() ? new Color(40, 40, 41) : UIUtil.getSlightlyDarkerColor(UIUtil.getSlightlyDarkerColor(result));
  }
}
