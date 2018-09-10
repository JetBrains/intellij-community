/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.filter;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.ClickListener;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;

public abstract class VcsLogPopupComponent extends JPanel {
  private static final int GAP_BEFORE_ARROW = 3;
  private static final int BORDER_SIZE = 2;

  @NotNull protected final String myName;
  @NotNull private JLabel myNameLabel;
  @NotNull private JLabel myValueLabel;

  protected VcsLogPopupComponent(@NotNull String name) {
    myName = name;
  }

  public JComponent initUi() {
    myNameLabel = new JLabel(myName + ": ");
    myValueLabel = new JLabel() {
      @Override
      public String getText() {
        return getCurrentText();
      }
    };
    setDefaultForeground();
    setFocusable(true);
    setBorder(createUnfocusedBorder());

    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    add(myNameLabel);
    add(myValueLabel);
    add(Box.createHorizontalStrut(GAP_BEFORE_ARROW));
    add(new JLabel(AllIcons.Ide.Statusbar_arrows));

    installChangeListener(() -> {
      myValueLabel.revalidate();
      myValueLabel.repaint();
    });
    showPopupMenuOnClick();
    showPopupMenuFromKeyboard();
    indicateHovering();
    indicateFocusing();
    return this;
  }


  public abstract String getCurrentText();

  public abstract void installChangeListener(@NotNull Runnable onChange);

  /**
   * Create popup actions available under this filter.
   */
  protected abstract ActionGroup createActionGroup();

  private void indicateFocusing() {
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(@NotNull FocusEvent e) {
        setBorder(createFocusedBorder());
      }

      @Override
      public void focusLost(@NotNull FocusEvent e) {
        setBorder(createUnfocusedBorder());
      }
    });
  }

  private void showPopupMenuFromKeyboard() {
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(@NotNull KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER || e.getKeyCode() == KeyEvent.VK_DOWN) {
          showPopupMenu();
        }
      }
    });
  }

  private void showPopupMenuOnClick() {
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        showPopupMenu();
        return true;
      }
    }.installOn(this);
  }

  private void indicateHovering() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(@NotNull MouseEvent e) {
        setOnHoverForeground();
      }

      @Override
      public void mouseExited(@NotNull MouseEvent e) {
        setDefaultForeground();
      }
    });
  }

  private void setDefaultForeground() {
    myNameLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor());
    myValueLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor().darker().darker());
  }

  private void setOnHoverForeground() {
    myNameLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextAreaForeground());
    myValueLabel.setForeground(UIUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextFieldForeground());
  }

  private void showPopupMenu() {
    ListPopup popup = createPopupMenu();
    popup.showUnderneathOf(this);
  }

  @NotNull
  protected ListPopup createPopupMenu() {
    return JBPopupFactory.getInstance().
      createActionGroupPopup(null, createActionGroup(), DataManager.getInstance().getDataContext(this),
                             JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
  }

  private static Border createFocusedBorder() {
    return BorderFactory.createCompoundBorder(new FilledRoundedBorder(UIUtil.getFocusedBorderColor(), 10, BORDER_SIZE),
                                              JBUI.Borders.empty(2));
  }

  private static Border createUnfocusedBorder() {
    return BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(BORDER_SIZE, BORDER_SIZE, BORDER_SIZE, BORDER_SIZE),
                                              JBUI.Borders.empty(2));
  }

  private static class FilledRoundedBorder extends LineBorder {
    private final int myArcSize;

    FilledRoundedBorder(@NotNull Color color, int arcSize, int thickness) {
      super(color, thickness);
      myArcSize = arcSize;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

      g.setColor(lineColor);
      Area area = new Area(new RoundRectangle2D.Double(x, y, width, height, myArcSize, myArcSize));
      int innerArc = Math.max(myArcSize - thickness, 0);
      area.subtract(new Area(new RoundRectangle2D.Double(x + thickness, y + thickness,
                                                         width - 2 * thickness, height - 2 * thickness,
                                                         innerArc, innerArc)));
      ((Graphics2D)g).fill(area);

      config.restore();
    }
  }
}
