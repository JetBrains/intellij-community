package com.intellij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author evgeny zakrevsky
 */
public class HideableTitledPanel extends JPanel {

  private TitledSeparatorWithMnemonic myTitledSeparator;
  private boolean myOn;
  private final JComponent myContent;
  private Dimension myPreviousContentSize;

  public HideableTitledPanel(String title, JComponent content, boolean on) {
    super(new BorderLayout());
    myContent = content;
    add(myContent, BorderLayout.CENTER);
    myTitledSeparator = new TitledSeparatorWithMnemonic("", null);
    add(myTitledSeparator, BorderLayout.NORTH);
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
    myTitledSeparator.getLabel().setIcon(AllIcons.General.ComboArrowDown);
    myTitledSeparator.getLabel().setIconTextGap(5);
    myContent.setVisible(true);
    adjustWindow();
    invalidate();
    repaint();
  }

  protected void off() {
    myOn = false;
    myTitledSeparator.getLabel().setIcon(AllIcons.General.ComboArrowRight);
    myTitledSeparator.getLabel().setIconTextGap(5 + AllIcons.General.ComboArrowDown.getIconWidth() - AllIcons.General.ComboArrowRight.getIconWidth());
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

  @Override
  public void addNotify() {
    super.addNotify();
    final int mnemonicIndex = UIUtil.getDisplayMnemonicIndex(getTitle());
    if (mnemonicIndex != -1) {
      getActionMap().put("tt", new AbstractAction() {
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
      getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(mnemonicCharacter, InputEvent.ALT_MASK, false), "tt");
    }
  }
}
