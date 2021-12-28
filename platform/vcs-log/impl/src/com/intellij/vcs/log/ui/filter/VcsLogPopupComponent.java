// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.filter;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionGroupUtil;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ClickListener;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.gridLayout.Gaps;
import com.intellij.ui.popup.PopupState;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import com.intellij.vcs.log.VcsLogBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

public abstract class VcsLogPopupComponent extends JPanel {
  private static final int GAP_BEFORE_ARROW = 3;
  protected static final int BORDER_SIZE = 2;
  protected static final int ARC_SIZE = 10;

  private final PopupState<JBPopup> myPopupState = PopupState.forPopup();
  @NotNull private final Supplier<@NlsContexts.Label String> myDisplayName;
  @Nullable private JLabel myNameLabel;
  @NotNull private JLabel myValueLabel;

  protected VcsLogPopupComponent(@NotNull Supplier<@NlsContexts.Label String> displayName) {
    myDisplayName = displayName;
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, Gaps.EMPTY);
  }

  public JComponent initUi() {
    myNameLabel = shouldDrawLabel() ? new DynamicLabel(() -> myDisplayName.get() + ": ") : null;
    myValueLabel = new DynamicLabel(this::getCurrentText);
    setDefaultForeground();
    setFocusable(true);
    setBorder(wrapBorder(createUnfocusedBorder()));

    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    if (myNameLabel != null) add(myNameLabel);
    add(myValueLabel);
    add(Box.createHorizontalStrut(GAP_BEFORE_ARROW));
    add(new JLabel(AllIcons.Ide.Statusbar_arrows));

    installChangeListener(() -> {
      myValueLabel.revalidate();
      myValueLabel.repaint();
    });
    showPopupMenuOnClick();
    showPopupMenuFromKeyboard();
    if (shouldIndicateHovering()) {
      indicateHovering();
    }
    indicateFocusing();
    return this;
  }


  public abstract String getCurrentText();

  public abstract void installChangeListener(@NotNull Runnable onChange);

  @NotNull
  protected Color getDefaultSelectorForeground() {
    return StartupUiUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor().darker().darker();
  }

  protected boolean shouldIndicateHovering() {
    return true;
  }

  protected boolean shouldDrawLabel() {
    return true;
  }

  /**
   * Create popup actions available under this filter.
   */
  protected abstract ActionGroup createActionGroup();

  private void indicateFocusing() {
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(@NotNull FocusEvent e) {
        setBorder(wrapBorder(createFocusedBorder()));
      }

      @Override
      public void focusLost(@NotNull FocusEvent e) {
        setBorder(wrapBorder(createUnfocusedBorder()));
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
    if (myNameLabel != null) {
      myNameLabel.setForeground(StartupUiUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getInactiveTextColor());
    }
    myValueLabel.setForeground(getDefaultSelectorForeground());
  }

  private void setOnHoverForeground() {
    if (myNameLabel != null) {
      myNameLabel.setForeground(StartupUiUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextAreaForeground());
    }
    myValueLabel.setForeground(StartupUiUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextFieldForeground());
  }

  void showPopupMenu() {
    if (myPopupState.isRecentlyHidden()) return; // do not show new popup
    ListPopup popup = createPopupMenu();
    myPopupState.prepareToShow(popup);
    popup.showUnderneathOf(this);
  }

  @NotNull
  protected ListPopup createPopupMenu() {
    return JBPopupFactory.getInstance().
      createActionGroupPopup(null, ActionGroupUtil.forceRecursiveUpdateInBackground(createActionGroup()), DataManager.getInstance().getDataContext(this),
                             JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false);
  }

  protected Border createFocusedBorder() {
    return new FilledRoundedBorder(UIUtil.getFocusedBorderColor(), ARC_SIZE, BORDER_SIZE, false);
  }

  protected Border createUnfocusedBorder() {
    return JBUI.Borders.empty(BORDER_SIZE);
  }

  private static Border wrapBorder(Border outerBorder) {
    return BorderFactory.createCompoundBorder(outerBorder, JBUI.Borders.empty(2));
  }

  public static class FilledRoundedBorder implements Border {
    private final Color myColor;
    private final int myThickness;
    private final int myArcSize;
    private final boolean myThinBorder;

    public FilledRoundedBorder(@NotNull Color color, int arcSize, int thickness, boolean thinBorder) {
      myColor = color;
      myThickness = thickness;
      myArcSize = arcSize;
      myThinBorder = thinBorder;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

      g.setColor(myColor);

      int thickness = JBUI.scale(myThinBorder ? 1 : myThickness);
      int arcSize = JBUI.scale(myArcSize);
      Area area = new Area(new RoundRectangle2D.Double(x, y, width, height, arcSize, arcSize));
      int innerArc = Math.max(arcSize - thickness, 0);
      area.subtract(new Area(new RoundRectangle2D.Double(x + thickness, y + thickness,
                                                         width - 2 * thickness, height - 2 * thickness,
                                                         innerArc, innerArc)));
      ((Graphics2D)g).fill(area);

      config.restore();
    }

    @Override
    public Insets getBorderInsets(Component c) {
      return JBUI.insets(myThickness);
    }

    @Override
    public boolean isBorderOpaque() {
      return false;
    }
  }

  private static final class DynamicLabel extends JLabel {
    private final Supplier<@NlsContexts.Label String> myText;

    private DynamicLabel(@NotNull Supplier<@NlsContexts.Label String> text) {myText = text;}

    @Override
    @NlsContexts.Label
    public String getText() {
      if (myText == null) return "";
      return myText.get();
    }
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleVcsLogPopupComponent(super.getAccessibleContext());
    }
    return accessibleContext;
  }

  private class AccessibleVcsLogPopupComponent extends AccessibleContextDelegate {

    AccessibleVcsLogPopupComponent(AccessibleContext context) {
      super(context);
    }

    @Override
    protected Container getDelegateParent() {
      return null;
    }

    @Override
    public String getAccessibleName() {
      return VcsLogBundle.message("vcs.log.filter.accessible.name", myNameLabel.getText(), myValueLabel.getText());
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.POPUP_MENU;
    }
  }
}
