// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ClickListener;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.gridLayout.UnscaledGaps;
import com.intellij.util.ui.accessibility.AccessibleContextDelegate;
import com.intellij.vcs.log.VcsLogBundle;
import org.jetbrains.annotations.Nls;
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
import java.util.stream.Stream;

import static com.intellij.openapi.util.Predicates.nonNull;

public abstract class FilterComponent extends JBPanel<FilterComponent> {
  private static final int GAP_BEFORE_ARROW = 3;
  protected static final int BORDER_SIZE = 2;
  protected static final int ARC_SIZE = 10;

  @NotNull private final Supplier<@NlsContexts.Label @NotNull String> myDisplayName;
  @Nullable private JLabel myNameLabel;
  @NotNull private JLabel myValueLabel;
  @NotNull private InlineIconButton myFilterActionButton;
  @Nullable private Runnable myShowPopupAction;

  protected FilterComponent(@NotNull Supplier<@NlsContexts.Label @NotNull String> displayName) {
    super(null);
    myDisplayName = displayName;
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGaps.EMPTY);
  }

  public JComponent initUi() {
    DrawLabelMode drawLabelMode = shouldDrawLabel();
    switch (drawLabelMode) {
      case ALWAYS -> myNameLabel = new DynamicLabel(() -> myDisplayName.get() + (isValueSelected() ? ": " : ""));
      case NEVER -> myNameLabel = null;
      case WHEN_VALUE_NOT_SET -> myNameLabel = new DynamicLabel(() -> isValueSelected() ? "" : myDisplayName.get());
      default -> throw new IllegalStateException("Unexpected value: " + drawLabelMode);
    }
    myValueLabel = new DynamicLabel(this::getCurrentText);
    setDefaultForeground();
    setFocusable(true);
    setBorder(wrapBorder(createUnfocusedBorder()));

    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));

    myFilterActionButton = new InlineIconButton(AllIcons.Actions.Close);

    if (myNameLabel != null) add(myNameLabel);
    add(myValueLabel);
    add(Box.createHorizontalStrut(GAP_BEFORE_ARROW));
    add(myFilterActionButton);

    myFilterActionButton.setActionListener(e -> {
      if (isValueSelected()) {
        resetFilter();
      }
      else {
        showPopup();
      }
    });

    updateFilterButton();

    installChangeListener(() -> {
      setDefaultForeground();
      updateFilterButton();

      myValueLabel.revalidate();
      myValueLabel.repaint();
    });
    showPopupMenuOnClick();
    showPopupMenuFromKeyboard();
    if (shouldIndicateHovering()) {
      Stream.of(this, myFilterActionButton, myNameLabel, myValueLabel)
        .filter(nonNull())
        .forEach(this::indicateHovering);
    }
    indicateFocusing();
    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return this;
  }

  private void updateFilterButton() {
    boolean selected = isValueSelected();
    myFilterActionButton.setIcon(selected ? AllIcons.Actions.Close : AllIcons.General.ArrowDown);
    myFilterActionButton.setHoveredIcon(selected ? AllIcons.Actions.CloseHovered : AllIcons.General.ArrowDown);
    myFilterActionButton.setFocusable(selected);
  }

  public abstract @NotNull @Nls String getCurrentText();

  /**
   * @return text that shows when filter value not selected (e.g. "All")
   */
  @NotNull
  @Nls
  public abstract String getEmptyFilterValue();

  protected abstract boolean isValueSelected();

  public abstract void installChangeListener(@NotNull Runnable onChange);

  protected boolean shouldIndicateHovering() {
    return true;
  }

  @NotNull
  protected DrawLabelMode shouldDrawLabel() {
    return DrawLabelMode.ALWAYS;
  }

  /**
   * @return an action that resets filter to its default state
   */
  protected abstract Runnable createResetAction();

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
          showPopup();
        }
      }
    });
  }

  private void showPopupMenuOnClick() {
    ClickListener clickListener = new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent event, int clickCount) {
        if (UIUtil.isCloseClick(event, MouseEvent.MOUSE_RELEASED)) {
          resetFilter();
        }
        else {
          showPopup();
        }
        return true;
      }
    };
    clickListener.installOn(this);
    clickListener.installOn(myValueLabel);
    if (myNameLabel != null) clickListener.installOn(myNameLabel);
  }

  private void indicateHovering(@NotNull JComponent component) {
    component.addMouseListener(new MouseAdapter() {
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
      myNameLabel.setForeground(UIUtil.getLabelInfoForeground());
    }
    myValueLabel.setForeground(UIUtil.getLabelForeground());
  }

  private void setOnHoverForeground() {
    if (myNameLabel != null) {
      myNameLabel.setForeground(StartupUiUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextAreaForeground());
    }
    myValueLabel.setForeground(StartupUiUtil.isUnderDarcula() ? UIUtil.getLabelForeground() : UIUtil.getTextFieldForeground());
  }

  private void resetFilter() {
    Runnable resetAction = createResetAction();
    if (resetAction != null) {
      resetAction.run();
    }
  }

  private void showPopup() {
    if (myShowPopupAction != null) {
      myShowPopupAction.run();
    }
  }

  protected Border createFocusedBorder() {
    return new FilledRoundedBorder(UIUtil.getFocusedBorderColor(), ARC_SIZE, BORDER_SIZE, false);
  }

  protected Border createUnfocusedBorder() {
    return JBUI.Borders.empty(BORDER_SIZE);
  }

  public void setShowPopupAction(@NotNull Runnable showPopupAction) {
    this.myShowPopupAction = showPopupAction;
  }

  private static Border wrapBorder(Border outerBorder) {
    return BorderFactory.createCompoundBorder(outerBorder, JBUI.Borders.empty(2));
  }

  public enum DrawLabelMode {
    ALWAYS,
    NEVER,
    WHEN_VALUE_NOT_SET
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
    private final @NotNull Supplier<@NlsContexts.Label String> myText;

    private DynamicLabel(@NotNull Supplier<@NlsContexts.Label String> text) { myText = text; }

    @Override
    @NlsContexts.Label
    public String getText() {
      //noinspection ConstantValue -- can be called during superclass initialization
      if (myText == null) return "";
      return myText.get();
    }

    @Override
    public Dimension getMinimumSize() {
      Dimension size = super.getMinimumSize();
      size.width = Math.min(size.width, JBUI.scale(70));
      return size;
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
      return VcsLogBundle.message("vcs.log.filter.accessible.name", myDisplayName.get(), getCurrentText());
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.POPUP_MENU;
    }
  }
}
