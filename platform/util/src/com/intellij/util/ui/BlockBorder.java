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
package com.intellij.util.ui;

import com.intellij.ui.Gray;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Eugene Belyaev
 */
public class BlockBorder implements Border {

  private static final Insets DEFAULT_INSETS = new Insets(1, 1, 3, 3);

  private static final Color DEFAULT_SHADE1 = Gray._203;
  private static final Color DEFAULT_SHADE2 = Gray._238;

  private static final Insets EMPTY = new Insets(0, 0, 0, 0);
  private Insets myInsets;
  private Insets myOuterMargin;
  private Color myBoundsColor = Color.GRAY;

  private Color myShade1;
  private Color myShade2;

  public BlockBorder() {
    this(null, null, DEFAULT_SHADE1, DEFAULT_SHADE2);
  }

  public BlockBorder(Insets outerMargin, Insets innerMargin) {
    this(outerMargin, innerMargin, DEFAULT_SHADE1, DEFAULT_SHADE2);
  }

  public BlockBorder(Insets outerMargin, Insets innerMargin, Color aShade1, Color aShade2) {
    if (outerMargin == null) {
      outerMargin = EMPTY;
    }
    myOuterMargin = (Insets)outerMargin.clone();
    myInsets = (Insets)outerMargin.clone();
    myInsets.top += DEFAULT_INSETS.top;
    myInsets.left += DEFAULT_INSETS.left;
    myInsets.bottom += DEFAULT_INSETS.bottom;
    myInsets.right += DEFAULT_INSETS.right;

    if (innerMargin == null) {
      innerMargin = EMPTY;
    }
    myInsets.top += innerMargin.top;
    myInsets.left += innerMargin.left;
    myInsets.bottom += innerMargin.bottom;
    myInsets.right += innerMargin.right;

    myShade1 = aShade1;
    myShade2 = aShade2;
  }

  public boolean isBorderOpaque() {
    return true;
  }

  public void paintBorder(Component component, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g;

    g2.setPaint(getBoundsColor());
    int horMargin = myOuterMargin.left + myOuterMargin.right;
    int vertMargin = myOuterMargin.top + myOuterMargin.bottom;

    g2.drawRect(x + myOuterMargin.left, y + myOuterMargin.top, x + width - 3 - horMargin, y + height - 3 - vertMargin);
    g2.setPaint(myShade1);

    g2.drawLine(x + 1 + myOuterMargin.left, y + height - 2 - myOuterMargin.bottom, x + width - 2 - myOuterMargin.right,
                y + height - 2 - myOuterMargin.bottom);
    g2.drawLine(x + width - 2 - myOuterMargin.right, y + 1 + myOuterMargin.bottom, x + width - 2 - myOuterMargin.right,
                y + height - 2 - myOuterMargin.bottom);

    g2.setPaint(myShade2);
    g2.drawLine(x + 2 + myOuterMargin.left, y + height - 1 - myOuterMargin.bottom, x + width - 1 - myOuterMargin.right,
                y + height - 1 - myOuterMargin.bottom);
    g2.drawLine(x + width - 1 - myOuterMargin.right, y + 2 + myOuterMargin.top, x + width - 1 - myOuterMargin.right,
                y + height - 1 - myOuterMargin.bottom);
  }

  private Color getBoundsColor() {
    return myBoundsColor;
  }

  public void setBoundsColor(Color aColor) {
    myBoundsColor = aColor;
  }

  public Insets getBorderInsets(Component component) {
    return myInsets;
  }

  public static void main(String[] args) {
    final JFrame jFrame = new JFrame();

    jFrame.getContentPane().setLayout(new BorderLayout());
    jFrame.getContentPane().setBackground(Color.white);

    final JPanel jPanel = new JPanel(new BorderLayout());
    jPanel.setBackground(Color.white);
    jPanel.setOpaque(true);

    jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10),
                                                        new BlockBorder(new Insets(5, 5, 5, 5), new Insets(5, 5, 5, 5))));
    jFrame.getContentPane().add(jPanel);

    jFrame.setBounds(100, 100, 200, 200);


    jFrame.setVisible(true);
  }
}
