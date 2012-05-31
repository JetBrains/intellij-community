/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.progress.util;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class ProgressBar extends JComponent {
  private double myFraction = 0.0;

  private Icon myProgressIcon = AllIcons.General.Progress;

  public ProgressBar() {
    updateUI();
  }

  public void setProgressIcon(@NotNull Icon progressIcon){
    myProgressIcon = progressIcon;
    repaint();
  }

  public double getFraction() {
    return myFraction;
  }

  public void setFraction(double fraction) {
    myFraction = fraction;
    repaint();
  }

  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (myFraction == 0 || myProgressIcon == null){
      return;
    }
    if (myFraction > 1){
      myFraction = 1;
    }
    int bricksTotal = (getWidth() - 4) / (myProgressIcon.getIconWidth() + 2);
    int bricksToDraw = (int)(bricksTotal * myFraction + 0.5);

    int rWidth = (myProgressIcon.getIconWidth() + 2) * bricksTotal + 1;
    int rHeight = myProgressIcon.getIconHeight() + 3;

    g.drawRoundRect(0, (getHeight() - rHeight)/2 , rWidth, rHeight, 2, 2 );
    int offset = 2;
    for (int i=0; i<bricksToDraw; i++) {
      myProgressIcon.paintIcon(this, g, offset, 2 + (getHeight() - rHeight)/2);
      offset += myProgressIcon.getIconWidth() + 2;
    }
  }

  public Dimension getPreferredSize() {
    return new Dimension(super.getPreferredSize().width,myProgressIcon.getIconHeight() + 4);
  }
}
