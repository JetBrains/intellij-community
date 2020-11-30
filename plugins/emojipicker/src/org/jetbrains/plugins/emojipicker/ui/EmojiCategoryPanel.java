// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.ui;

import com.intellij.ui.InplaceButton;
import com.intellij.ui.scale.JBUIScale;
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
    for (EmojiCategory category : categories) {
      @Nls String name = EmojiCategoriesBundle.findNameForCategory(category);
      InplaceButton button = new InplaceButton(name, category.getIcon(), e -> emojiPicker.selectCategory(category, true)) {
        @Override
        protected void paintComponent(Graphics g) {
          super.paintComponent(g);
          if (emojiPicker.getCurrentFocusTarget() == category) {
            int x = getWidth() / 2, y = getHeight() / 2;
            g.setColor(myStyle.myFocusBorderColor);
            g.drawRoundRect(x - 15, y - 15, 30, 30, 4, 4);
          }
          if (myActiveCategory == category) {
            g.setColor(myStyle.mySelectedCategoryColor);
            g.fillRect(0, getHeight() - 2, getWidth(), 2);
          }
        }
      };
      button.setPreferredSize(new Dimension(30, 30));
      add(button, gbc);
    }
  }

  @Override
  public void paintComponent(Graphics g) {
    g.setColor(myStyle.myToolbarColor);
    g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
    g.fillRect(0, 6, getWidth(), getHeight() - 6);
    g.setColor(myStyle.myBorderColor);
    g.fillRect(0, getHeight() - 1, getWidth(), 1);
  }

  void selectCategory(EmojiCategory category) {
    myActiveCategory = category;
    repaint();
  }
}
