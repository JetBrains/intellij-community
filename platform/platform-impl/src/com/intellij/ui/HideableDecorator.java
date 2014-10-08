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
package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author evgeny zakrevsky
 */
public class HideableDecorator {

  private final JPanel myPanel;

  private final TitledSeparator myTitledSeparator;
  private final boolean myAdjustWindow;

  private boolean myOn;
  private JComponent myContent;
  private Dimension myPreviousContentSize;

  public HideableDecorator(JPanel panel, String title, boolean adjustWindow) {
    this(panel, title, adjustWindow, null);
  }

  public HideableDecorator(JPanel panel, String title, boolean adjustWindow, @Nullable JComponent northEastComponent) {
    myPanel = panel;
    myAdjustWindow = adjustWindow;
    myTitledSeparator = new TitledSeparator(title, null) {
      @Override
      public void addNotify() {
        super.addNotify();
        registerMnemonic();
      }
    };
    JPanel northPanel = new JPanel(new BorderLayout());
    northPanel.add(myTitledSeparator, BorderLayout.CENTER);
    if (northEastComponent != null) {
      northPanel.add(northEastComponent, BorderLayout.EAST);
    }

    myPanel.add(northPanel, BorderLayout.NORTH);
    myTitledSeparator.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    updateIcon();
    myTitledSeparator.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(@NotNull MouseEvent e) {
        if (myOn) {
          off();
        }
        else {
          on();
        }
      }
    });
  }

  private void updateIcon() {
    final Icon icon = myOn ? AllIcons.General.SplitDown : AllIcons.General.SplitRight;
    myTitledSeparator.getLabel().setIcon(icon);
    myTitledSeparator.getLabel().setDisabledIcon(IconLoader.getTransparentIcon(icon, 0.5f));
  }

  public void setContentComponent(@Nullable JComponent content) {
    if (content == null && myContent != null) {
      myPanel.remove(myContent);
    }
    myContent = content;
    if (myContent != null) {
      myContent.setVisible(myOn);
      myPanel.add(myContent, BorderLayout.CENTER);
    }
  }

  public void setOn(boolean on) {
    myOn = on;
    if (myOn) {
      on();
    }
    else {
      off();
    }
  }

  public boolean isExpanded() {
    return myOn;
  }

  public void setTitle(String title) {
    myTitledSeparator.setText(title);
  }

  public String getTitle() {
    return myTitledSeparator.getText();
  }

  protected void on() {
    myOn = true;
    updateIcon();
    myTitledSeparator.getLabel().setIconTextGap(5);
    if (myContent != null) {
      myContent.setVisible(true);
    }
    adjustWindow();
    myPanel.invalidate();
    myPanel.repaint();
  }

  protected void off() {
    myOn = false;
    updateIcon();
    if (myContent != null) {
      myContent.setVisible(false);
      myPreviousContentSize = myContent.getSize();
    }
    adjustWindow();
    myPanel.invalidate();
    myPanel.repaint();
  }

  private void adjustWindow() {
    if (!myAdjustWindow) return;
    final Window window = SwingUtilities.getWindowAncestor(myPanel);
    if (window == null) return;
    final Dimension size = window.getSize();
    final Dimension contentSize = myPreviousContentSize != null && myPreviousContentSize.width > 0 && myPreviousContentSize.height > 0
                                  ? myPreviousContentSize
                                  : myContent.getPreferredSize();
    final Dimension newSize;
    if (myOn) {
      newSize = new Dimension(Math.max(size.width, myContent.getSize().width), size.height + contentSize.height);
    }
    else {
      newSize = new Dimension(size.width, size.height - contentSize.height);
    }
    if (!newSize.equals(size)) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (window.isShowing()) {
            window.setSize(newSize);
          }
        }
      });
    }
  }

  public void setEnabled(boolean enabled) {
    myTitledSeparator.setEnabled(enabled);
    myContent.setEnabled(enabled);
  }

  private void registerMnemonic() {
    final int mnemonicIndex = UIUtil.getDisplayMnemonicIndex(getTitle());
    if (mnemonicIndex != -1) {
      myPanel.getActionMap().put("Collapse/Expand on mnemonic", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myOn) {
            off();
          }
          else {
            on();
          }
        }
      });
      final Character mnemonicCharacter = UIUtil.removeMnemonic(getTitle()).toUpperCase().charAt(mnemonicIndex);
      myPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
        .put(KeyStroke.getKeyStroke(mnemonicCharacter, InputEvent.ALT_MASK, false), "Collapse/Expand on mnemonic");
    }
  }
}
