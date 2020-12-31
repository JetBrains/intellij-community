// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.ui;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.InplaceButton;
import org.jetbrains.plugins.emojipicker.EmojiSkinTone;
import org.jetbrains.plugins.emojipicker.service.EmojiService;

import javax.swing.*;
import java.awt.*;
import java.awt.font.TextLayout;
import java.util.EnumMap;
import java.util.Map;

public class EmojiSkinTonesPanel extends JPanel {
  private static final int ITEM_SIZE = 34;
  @NlsSafe private static final String ITEM_EMOJI = "🖐";

  private final EmojiPickerStyle myStyle;
  private final Map<EmojiSkinTone, Item> myItemMap = new EnumMap<>(EmojiSkinTone.class);
  private EmojiSkinTone myCurrentSkinTone = EmojiService.getInstance().getCurrentSkinTone();
  private int myFocusedItem = -1;

  EmojiSkinTonesPanel(EmojiPicker emojiPicker, EmojiPickerStyle style) {
    myStyle = style;
    setVisible(false);
    setLayout(new GridBagLayout());
    for (EmojiSkinTone tone : EmojiSkinTone.values()) {
      Item item = new Item(emojiPicker, tone);
      myItemMap.put(tone, item);
    }
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(myStyle.myBorderColor);
    g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
    g.setColor(myStyle.myBackgroundColor);
    g.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (myFocusedItem != -1) {
      g.setColor(myStyle.myFocusBorderColor);
      g.drawRoundRect(3, myFocusedItem * ITEM_SIZE + 3, ITEM_SIZE - 4, ITEM_SIZE - 4, 6, 6);
    }
  }

  EmojiSkinTone getCurrentSkinTone() {
    return myCurrentSkinTone;
  }

  Item getCurrentItem() {
    return myItemMap.get(myCurrentSkinTone);
  }

  void focusItem(int increment) {
    myFocusedItem = (myFocusedItem + increment + getComponentCount()) % getComponentCount();
    repaint();
  }

  void setCurrentItemFromFocus() {
    Component c = getComponent(myFocusedItem);
    for (Item item : myItemMap.values()) {
      if (item.myButton == c) {
        myCurrentSkinTone = item.myTone;
        return;
      }
    }
  }

  void open(JComponent textField) {
    myFocusedItem = -1;
    Insets i = textField.getInsets();
    setBounds(textField.getX() + textField.getWidth() - i.right - ITEM_SIZE - 1 - 6,
              textField.getY() + textField.getHeight() / 2 - ITEM_SIZE / 2 - 1,
              ITEM_SIZE + 2, ITEM_SIZE * myItemMap.size() + 2);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    removeAll();
    add(myItemMap.get(myCurrentSkinTone).myButton, gbc);
    for (Map.Entry<EmojiSkinTone, Item> item : myItemMap.entrySet()) {
      if (item.getKey() != myCurrentSkinTone) add(item.getValue().myButton, gbc);
    }
    doLayout();
    setVisible(true);
  }

  class Item {

    private final EmojiSkinTone myTone;
    private final Icon myIcon, myHoverIcon;
    private final InplaceButton myButton;

    private Item(EmojiPicker emojiPicker, EmojiSkinTone tone) {
      myTone = tone;
      myIcon = new Icon(false);
      myHoverIcon = new Icon(true);
      myButton = new InplaceButton(new IconButton("", myIcon, myHoverIcon), e -> {
        myCurrentSkinTone = tone;
        emojiPicker.selectSkinToneFromPanel();
      });
    }

    javax.swing.Icon getIcon(boolean hover) {
      return hover ? myHoverIcon : myIcon;
    }


    private class Icon implements javax.swing.Icon {
      private final boolean myHover;

      private Icon(boolean hover) { myHover = hover; }

      @Override
      public void paintIcon(Component c, Graphics g, int x, int y) {
        ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, AntialiasingType.getKeyForCurrentScope(false));
        if (myHover) {
          g.setColor(myStyle.myHoverBackgroundColor);
          g.fillRoundRect(x, y, getIconWidth(), getIconHeight(), 6, 6);
        }

        @NlsSafe String emoji = ITEM_EMOJI + myTone.getStringValue();
        FontMetrics metrics = g.getFontMetrics(myStyle.myEmojiFont);
        int verticalOffset = metrics.getHeight() / 2 - metrics.getDescent();
        int width = metrics.stringWidth(emoji);
        new TextLayout(emoji, myStyle.myEmojiFont, ((Graphics2D)g).getFontRenderContext())
          .draw((Graphics2D)g, x + (getIconWidth() - width) / 2F, y + getIconHeight() / 2F + verticalOffset);
      }

      @Override
      public int getIconWidth() {
        return ITEM_SIZE;
      }

      @Override
      public int getIconHeight() {
        return ITEM_SIZE;
      }
    }
  }
}
