// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.ui;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.openapi.ui.popup.IconButton;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.paint.RectanglePainter2D;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.plugins.emojipicker.EmojiSkinTone;
import org.jetbrains.plugins.emojipicker.service.EmojiService;

import javax.swing.*;
import java.awt.*;
import java.awt.font.TextLayout;
import java.util.EnumMap;
import java.util.Map;

public class EmojiSkinTonesPanel extends JPanel {
  @NlsSafe private static final String ITEM_EMOJI = "🖐";

  private final EmojiPickerStyle myStyle;
  private final Map<EmojiSkinTone, Item> myItemMap = new EnumMap<>(EmojiSkinTone.class);
  private final int myItemSize = JBUIScale.scale(34);
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
    RectanglePainter2D.FILL.paint((Graphics2D)g, 0, 0, getWidth(), getHeight(), 6.0);
    g.setColor(myStyle.myBackgroundColor);
    RectanglePainter2D.FILL.paint((Graphics2D)g, 1, 1, getWidth() - 2, getHeight() - 2, 6.0);
  }

  @Override
  public void paint(Graphics g) {
    super.paint(g);
    if (myFocusedItem != -1) {
      g.setColor(myStyle.myFocusBorderColor);
      RectanglePainter2D.DRAW.paint((Graphics2D)g, 3, myFocusedItem * myItemSize + 3, myItemSize - 4, myItemSize - 4, 6.0);
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
    if (myFocusedItem < 0) return;
    Component c = getComponent(myFocusedItem);
    for (Item item : myItemMap.values()) {
      if (item.myButton == c) {
        myCurrentSkinTone = item.myTone;
        return;
      }
    }
  }

  void open(EmojiSearchField searchField) {
    myFocusedItem = -1;
    Point center = searchField.getSkinToneIconCenter();
    setBounds(searchField.getX() + center.x - myItemSize / 2 - 1,
              searchField.getY() + center.y - myItemSize / 2 - 1,
              myItemSize + 2, myItemSize * myItemMap.size() + 2);
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
          RectanglePainter2D.FILL.paint((Graphics2D)g, x, y, getIconWidth(), getIconHeight(), 6.0);
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
        return myItemSize;
      }

      @Override
      public int getIconHeight() {
        return myItemSize;
      }
    }
  }
}
