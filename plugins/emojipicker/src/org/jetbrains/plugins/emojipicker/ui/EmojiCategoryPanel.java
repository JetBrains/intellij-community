// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.ui;

import com.intellij.ui.InplaceButton;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.plugins.emojipicker.EmojiCategory;
import org.jetbrains.plugins.emojipicker.messages.EmojiCategoriesBundle;

import javax.swing.*;
import java.awt.*;
import java.util.List;

class EmojiCategoryPanel extends JPanel {
  private final EmojiPickerStyle myStyle;
  private EmojiCategory myActiveCategory;

  EmojiCategoryPanel(EmojiPicker emojiPicker, EmojiPickerStyle style, List<EmojiCategory> categories) {
    myStyle = style;
    Dimension size = new Dimension(0, JBUIScale.scale(39));
    setPreferredSize(size);
    setMinimumSize(size);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.weighty = 1;
    gbc.gridy = 0;
    gbc.insets = JBUI.insets(0, 4);
    setLayout(new GridBagLayout());
    final JBDimension buttonSize = new JBDimension(30, 30);
    for (EmojiCategory category : categories) {
      @Nls String name = EmojiCategoriesBundle.findNameForCategory(category);
      InplaceButton button = new InplaceButton(name, category.getIcon(), e -> emojiPicker.selectCategory(category, true)) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          if (emojiPicker.getCurrentFocusTarget() == category) {
            buttonSize.update();
            double x = getWidth() / 2.0, y = getHeight() / 2.0;
            g.setColor(myStyle.myFocusBorderColor);
            RectanglePainter2D.DRAW.paint((Graphics2D)g, x - buttonSize.getWidth() / 2.0, y - buttonSize.getHeight() / 2.0,
                                          buttonSize.getWidth(), buttonSize.getHeight(), 4.0);
          }
          if (myActiveCategory == category) {
            double height = JBUIScale.scale(2F);
            g.setColor(myStyle.mySelectedCategoryColor);
            RectanglePainter2D.FILL.paint((Graphics2D)g, 0, getHeight() - height, getWidth(), height);
          }
        }
      };
      button.setPreferredSize(buttonSize);
      add(button, gbc);
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    g.setColor(myStyle.myToolbarColor);
    RectanglePainter2D.FILL.paint((Graphics2D)g, 0, 0, getWidth(), getHeight(), 6.0);
    RectanglePainter2D.FILL.paint((Graphics2D)g, 0, 6, getWidth(), getHeight() - 6);
    g.setColor(myStyle.myBorderColor);
    RectanglePainter2D.FILL.paint((Graphics2D)g, 0, getHeight() - myStyle.myBorder.getFloat(), getWidth(), myStyle.myBorder.getFloat());
  }

  void selectCategory(EmojiCategory category) {
    myActiveCategory = category;
    repaint();
  }
}
