package com.intellij.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author evgeny zakrevsky
 */
public class HideableTitledPanel extends JPanel {
  Icon OFF_ICON = IconLoader.getIcon("/general/comboArrow.png");
  Icon ON_ICON = IconLoader.getIcon("/general/comboUpPassive.png");
  private TitledSeparatorWithMnemonic myTitledSeparator;
  private boolean myOn;
  private final JComponent myContent;
  private Dimension myPreviousContentSize;

  public HideableTitledPanel(String title, JComponent content, boolean on) {
    super(new BorderLayout());
    myContent = content;
    add(myContent, BorderLayout.CENTER);
    myTitledSeparator = new TitledSeparatorWithMnemonic(title, null);
    add(myTitledSeparator, BorderLayout.NORTH);
    myTitledSeparator.getLabel().setIcon(OFF_ICON);

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

      @Override
      public void mouseEntered(MouseEvent e) {
        setCursor(new Cursor(Cursor.HAND_CURSOR));
      }

      @Override
      public void mouseExited(MouseEvent e) {
        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
      }
    });

    UIUtil.setActionNameAndMnemonic(title, new AbstractAction() {
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

    setOn(on);
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

  protected void on() {
    myOn = true;
    myTitledSeparator.getLabel().setIcon(ON_ICON);
    myContent.setVisible(true);
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

  protected void off() {
    myOn = false;
    myTitledSeparator.getLabel().setIcon(OFF_ICON);
    myContent.setVisible(false);
    myPreviousContentSize = myContent.getSize();
    adjustWindow();
    invalidate();
    repaint();
  }

  public void setTitle(String title) {
    myTitledSeparator.setText(title);
  }

  @Override
  public void setEnabled(boolean enabled) {
    myTitledSeparator.myLabel.setForeground(enabled ? UIUtil.getActiveTextColor() : UIUtil.getInactiveTextColor());
    myContent.setEnabled(enabled);
  }
}
