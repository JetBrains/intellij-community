// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import java.util.Locale;

/**
 * @author evgeny zakrevsky
 */
public class HideableDecorator {
  private static final String ACTION_KEY = "Collapse/Expand on mnemonic";

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
    Icon icon = myOn ? AllIcons.General.SplitDown : AllIcons.General.SplitRight;
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
    if (myOn) on(); else off();
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
    if (myAdjustWindow) {
      Window window = SwingUtilities.getWindowAncestor(myPanel);
      if (window != null) {
        Dimension contentSize = myPreviousContentSize;
        if (contentSize == null || contentSize.width <= 0 || contentSize.height <= 0) {
          contentSize = myContent.getPreferredSize();
        }

        Dimension size = window.getSize(), newSize;
        if (myOn) {
          newSize = new Dimension(Math.max(size.width, myContent.getSize().width), size.height + contentSize.height);
        }
        else {
          newSize = new Dimension(size.width, size.height - contentSize.height);
        }

        if (!newSize.equals(size)) {
          UIUtil.invokeLaterIfNeeded(() -> {
            if (window.isShowing()) {
              window.setSize(newSize);
            }
          });
        }
      }
    }
  }

  public void setEnabled(boolean enabled) {
    myTitledSeparator.setEnabled(enabled);
    myContent.setEnabled(enabled);
  }

  private void registerMnemonic() {
    int mnemonicIndex = UIUtil.getDisplayMnemonicIndex(getTitle());
    if (mnemonicIndex != -1) {
      myPanel.getActionMap().put(ACTION_KEY, new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myOn) off(); else on();
        }
      });
      Character c = UIUtil.removeMnemonic(getTitle()).toUpperCase(Locale.getDefault()).charAt(mnemonicIndex);
      myPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(c, InputEvent.ALT_MASK, false), ACTION_KEY);
    }
  }
}