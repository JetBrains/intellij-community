package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * @author evgeny zakrevsky
 */
public class HideableTitledPanel extends JPanel {
  private final static Icon OFF_ICON = AllIcons.General.ComboArrow;
  private final static Icon ON_ICON = AllIcons.General.ComboUpPassive;

  private TitledSeparatorWithMnemonic myTitledSeparator;
  private boolean myOn;
  private final JComponent myContent;
  private Dimension myPreviousContentSize;
  private boolean myMnemonicActionRegistered = false;

  public HideableTitledPanel(String title, JComponent content, boolean on) {
    super(new BorderLayout());
    myContent = content;
    add(myContent, BorderLayout.CENTER);
    myTitledSeparator = new TitledSeparatorWithMnemonic("", null);
    add(myTitledSeparator, BorderLayout.NORTH);
    myTitledSeparator.getLabel().setIcon(OFF_ICON);

    myTitledSeparator.getLabel().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myTitledSeparator.getLabel().addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (myOn) {
          off();
        }
        else {
          on();
        }
      }
    });

    setOn(on);
    setTitle(title);

    addHierarchyListener(new HierarchyListener() {
      @Override
      public void hierarchyChanged(HierarchyEvent e) {
        final JComponent rootPane = getRootPane();
        if (rootPane != null && !myMnemonicActionRegistered) {
          final int mnemonicIndex = UIUtil.getDisplayMnemonicIndex(getTitle());
          if (mnemonicIndex != -1) {
            AnAction action = new AnAction() {
              public void actionPerformed(AnActionEvent e) {
                if (myOn) {
                  off();
                }
                else {
                  on();
                }
              }
            };
            final Character mnemonicCharacter = UIUtil.removeMnemonic(getTitle()).toLowerCase().charAt(mnemonicIndex);
            action.registerCustomShortcutSet(
              new CustomShortcutSet(KeyStroke.getKeyStroke(mnemonicCharacter, InputEvent.ALT_MASK)), rootPane);
            myMnemonicActionRegistered = true;
          }
        }
      }
    });
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

  public void setTitle(String title) {
    myTitledSeparator.setText(title);
  }

  public String getTitle() {
    return myTitledSeparator.getText();
  }

  protected void on() {
    myOn = true;
    myTitledSeparator.getLabel().setIcon(ON_ICON);
    myContent.setVisible(true);
    adjustWindow();
    invalidate();
    repaint();
  }

  protected void off() {
    myOn = false;
    myTitledSeparator.getLabel().setIcon(OFF_ICON);
    myContent.setVisible(false);
    myPreviousContentSize = myContent.getSize();
    adjustWindow();
    invalidate();
    repaint();
  }

  private void adjustWindow() {
    final Window window = SwingUtilities.getWindowAncestor(this);
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

  @Override
  public void setEnabled(boolean enabled) {
    myTitledSeparator.myLabel.setForeground(enabled ? UIUtil.getActiveTextColor() : UIUtil.getInactiveTextColor());
    myContent.setEnabled(enabled);
  }
}
