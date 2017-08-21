/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionButtonComponent;
import com.intellij.ui.Gray;

import javax.swing.*;
import java.awt.*;

/**
 * A wrapper for an icon which paints it like a selected toggleable action in toolbar
 *
 * @author Konstantin Bulenkov
 */
public class PoppedIcon implements Icon {
  private final Icon myIcon;
  private final int myWidth;
  private final int myHeight;

  public PoppedIcon(Icon icon, int width, int height) {
    myIcon = icon;
    myWidth = width;
    myHeight = height;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    final Dimension size = new Dimension(getIconWidth() + 2*x, getIconHeight() + 2*x);
    IdeaActionButtonLook.paintBackground(g, size, Gray._235, ActionButtonComponent.POPPED);
    IdeaActionButtonLook.paintBorder(g, size, ActionButtonComponent.POPPED);
    myIcon.paintIcon(c, g, x + (getIconWidth() - myIcon.getIconWidth())/2, y + (getIconHeight() - myIcon.getIconHeight())/2);
  }

  @Override
  public int getIconWidth() {
    return myWidth;
  }

  @Override
  public int getIconHeight() {
    return myHeight;
  }
}
