// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.emojipicker.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.fields.ExtendableTextComponent;
import com.intellij.ui.components.fields.ExtendableTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.emojipicker.messages.EmojipickerBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

public class EmojiSearchField extends ExtendableTextField {

  private static final ExtendableTextComponent.Extension SEARCH_ICON_EXTENSION = new ExtendableTextComponent.Extension() {
    @Override
    public Icon getIcon(boolean hovered) {
      return AllIcons.Actions.Search;
    }

    @Override
    public boolean isIconBeforeText() {
      return true;
    }

    @Override
    public int getIconGap() {
      return JBUIScale.scale(6);
    }
  };

  private final EmojiPicker myEmojiPicker;
  private final EmojiPickerStyle myStyle;
  private final List<ExtendableTextComponent.Extension> extensions;

  EmojiSearchField(EmojiPicker emojiPicker, EmojiPickerStyle style) {
    myEmojiPicker = emojiPicker;
    myStyle = style;
    extensions = List.of(SEARCH_ICON_EXTENSION, new ExtendableTextComponent.Extension() {
      @Override
      public Icon getIcon(boolean hovered) {
        return emojiPicker.getCurrentSkinToneIcon(hovered);
      }

      @Override
      public boolean isIconBeforeText() {
        return false;
      }

      @Override
      public int getIconGap() {
        return JBUIScale.scale(6);
      }

      @Override
      public Runnable getActionOnClick() {
        return emojiPicker::openSkinToneSelectionPanel;
      }

      @Override
      public @NlsContexts.Tooltip String getTooltip() {
        return EmojipickerBundle.message("message.EmojiPicker.ChangeSkinTone");
      }
    });
    Dimension size = new Dimension(0, JBUIScale.scale(40));
    setPreferredSize(size);
    setMinimumSize(size);
    setExtensions(SEARCH_ICON_EXTENSION);
    setBorder(JBUI.Borders.empty(0, 12, 0, 8));
    setBackground(myStyle.myBackgroundColor);
    setFocusTraversalKeysEnabled(false);
    setLayout(new BorderLayout());
    getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        emojiPicker.search(getText());
      }
    });
    addKeyListener(new KeyAdapter() {
      void handle(KeyEvent e, boolean typed) {
        int c = typed ? e.getKeyChar() : e.getKeyCode();
        boolean mayIntercept = c == KeyEvent.VK_SPACE || c == KeyEvent.VK_ENTER;
        if (mayIntercept == typed && emojiPicker.handleKey(c, e.getModifiersEx())) {
          e.consume();
        }
      }

      @Override
      public void keyTyped(KeyEvent e) {
        handle(e, true);
      }

      @Override
      public void keyPressed(KeyEvent e) {
        handle(e, false);
      }
    });
  }

  void update() {
    setExtensions(extensions);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (myEmojiPicker.getCurrentFocusTarget() instanceof EmojiSkinTonesPanel) {
      g.setColor(myStyle.myFocusBorderColor);
      Insets i = getInsets();
      final int SIZE = 30;
      g.drawRoundRect(getWidth() - i.right - SIZE - 8, getHeight() / 2 - SIZE / 2, SIZE, SIZE, 6, 6);
    }
  }
}
