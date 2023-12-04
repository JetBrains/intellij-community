// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBUI.CurrentTheme.Editor.Tooltip;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;

public final class HintHint {
  private Component myOriginalComponent;
  private Point myOriginalPoint;

  private boolean myForcePopup;
  private boolean myAwtTooltip = false;
  private Balloon.Position myPreferredPosition = Balloon.Position.below;

  private boolean myContentActive = true;

  private boolean myQuickHint = false;
  private boolean myMayCenterTooltip = false;

  private Color myTextFg;
  private Color myTextBg;
  private Color myBorderColor;
  private Border myComponentBorder = null;
  private Insets myBorderInsets;
  private Font myFont;
  private Icon myStatusIcon;
  private int myCalloutShift;

  private boolean myExplicitClose;
  private int myPositionChangeX;
  private int myPositionChangeY;
  private boolean myShowImmediately = false;
  private boolean myAnimationEnabled;
  private boolean myRequestFocus;

  public static final String OVERRIDE_BORDER_KEY = "BorderInsets";

  public HintHint() {
  }

  public HintHint(MouseEvent e) {
    this(e.getComponent(), e.getPoint());
  }

  public HintHint(Editor editor, Point point) {
    this(editor.getContentComponent(), point);
  }

  public HintHint(Component originalComponent, Point originalPoint) {
    myOriginalComponent = originalComponent;
    myOriginalPoint = originalPoint;
  }

  public HintHint setAwtTooltip(boolean awtTooltip) {
    myAwtTooltip = awtTooltip;
    return this;
  }

  public HintHint setMayCenterPosition(boolean mayCenter) {
    myMayCenterTooltip = mayCenter;
    return this;
  }

  public boolean isMayCenterTooltip() {
    return myMayCenterTooltip;
  }

  public HintHint setPreferredPosition(Balloon.Position position) {
    myPreferredPosition = position;
    return this;
  }

  public boolean isAwtTooltip() {
    return myAwtTooltip;
  }

  public boolean isPopupForced() {
    return myForcePopup;
  }

  public HintHint setForcePopup(boolean forcePopup) {
    myForcePopup = forcePopup;
    return this;
  }

  public Component getOriginalComponent() {
    return myOriginalComponent;
  }

  public Point getOriginalPoint() {
    return myOriginalPoint;
  }

  public RelativePoint getTargetPoint() {
    return new RelativePoint(getOriginalComponent(), getOriginalPoint());
  }

  public Border getComponentBorder() {
    return myComponentBorder;
  }

  public void setComponentBorder(@Nullable Border border) {
    myComponentBorder = border;
  }

  public Balloon.Position getPreferredPosition() {
    return myPreferredPosition;
  }

  public Color getTextForeground() {
    return myTextFg != null ? myTextFg : getTooltipManager().getTextForeground(myAwtTooltip);
  }

  public Color getTextBackground() {
    return myTextBg != null ? myTextBg : getTooltipManager().getTextBackground(myAwtTooltip);
  }

  public Color getLinkForeground() {
    return getTooltipManager().getLinkForeground(myAwtTooltip);
  }

  public boolean isOwnBorderAllowed() {
    return getTooltipManager().isOwnBorderAllowed(myAwtTooltip);
  }

  public @NotNull Color getBorderColor() {
    return myBorderColor != null ? myBorderColor : JBUI.CurrentTheme.Tooltip.borderColor();
  }

  public boolean isBorderColorSet() {
    return myBorderColor != null;
  }

  public Insets getBorderInsets() {
    return myBorderInsets;
  }

  public boolean isOpaqueAllowed() {
    return getTooltipManager().isOpaqueAllowed(myAwtTooltip);
  }

  public Font getTextFont() {
    return myFont != null ? myFont : getTooltipManager().getTextFont(myAwtTooltip);
  }

  public Icon getStatusIcon() {
    return myStatusIcon;
  }

  public String getUlImg() {
    return getTooltipManager().getUlImg(myAwtTooltip);
  }

  public boolean isContentActive() {
    return myContentActive;
  }

  public boolean isExplicitClose() {
    return myExplicitClose;
  }

  public HintHint setContentActive(boolean active) {
    myContentActive = active;
    return this;
  }

  public HintHint setHighlighterType(boolean highlighter) {
    myQuickHint = highlighter;
    return this;
  }

  public boolean isHighlighterType() {
    return myQuickHint;
  }

  private static IdeTooltipManager getTooltipManager() {
    return IdeTooltipManager.getInstance();
  }

  public void initStyle(Component c, boolean includeChildren) {
    if (includeChildren) {
      for (Component component : UIUtil.uiTraverser(c)) {
        doInit(component);
      }
    }
    else {
      doInit(c);
    }
  }

  private void doInit(Component c) {
    c.setForeground(getTextForeground());
    c.setBackground(getTextBackground());
    c.setFont(getTextFont());
    if (c instanceof JComponent jc) {
      jc.setOpaque(isOpaqueAllowed());
      jc.setBorder(isOwnBorderAllowed() ? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black),
                                                                             BorderFactory.createEmptyBorder(0, 5, 0, 5)) : null);
    }
  }

  public void initStyleFrom(JComponent component) {
    myTextFg = component.getForeground();
    myTextBg = component.getBackground();
    myFont = component.getFont();
    boolean setDefaultStatus = true;
    HintUtil.HintLabel label = UIUtil.findComponentOfType(component, HintUtil.HintLabel.class);
    if (label != null) {
      HintHint componentHintLabel = label.getHintHint();
      if (componentHintLabel != null) {
        setBorderColor(componentHintLabel.getBorderColor());
        setBorderInsets(componentHintLabel.getBorderInsets());
        setComponentBorder(componentHintLabel.getComponentBorder());
        setDefaultStatus = false;
      }
    }
    if (setDefaultStatus) {
      setStatus(HintHint.Status.Info);
    }
    Object insets = component.getClientProperty(OVERRIDE_BORDER_KEY);
    if (insets instanceof Insets border) {
      setBorderInsets(border);
    }
  }

  public HintHint setTextFg(Color textFg) {
    myTextFg = textFg;
    return this;
  }

  public HintHint setTextBg(Color textBg) {
    myTextBg = textBg;
    return this;
  }

  public HintHint setFont(Font font) {
    myFont = font;
    return this;
  }

  public HintHint setBorderColor(Color borderColor) {
    myBorderColor = borderColor;
    return this;
  }

  public HintHint setBorderInsets(Insets insets) {
    myBorderInsets = insets;
    return this;
  }


  public int getCalloutShift() {
    return myCalloutShift;
  }

  public HintHint setCalloutShift(int calloutShift) {
    myCalloutShift = calloutShift;
    return this;
  }

  public HintHint setExplicitClose(boolean explicitClose) {
    myExplicitClose = explicitClose;
    return this;
  }

  public HintHint setPositionChangeShift(int x, int y) {
    myPositionChangeX = x;
    myPositionChangeY = y;
    return this;
  }

  public HintHint setStatus(@NotNull Status status) {
    if (ExperimentalUI.isNewUI()) {
      applyStatus(status);
    }
    return this;
  }

  public HintHint setStatus(@NotNull Status status, @NotNull Insets borderInsets) {
    if (ExperimentalUI.isNewUI()) {
      applyStatus(status);
      myBorderInsets = borderInsets;
    }
    return this;
  }

  public HintHint applyStatus(@NotNull Status status) {
    myTextFg = status.foreground;
    myTextBg = status.background;
    myBorderColor = status.border;
    myStatusIcon = status.icon;
    myBorderInsets = JBUI.insets(12, 12, 14, 12);
    myComponentBorder = JBUI.Borders.empty();
    return this;
  }

  public int getPositionChangeX() {
    return myPositionChangeX;
  }

  public int getPositionChangeY() {
    return myPositionChangeY;
  }

  public boolean isShowImmediately() {
    return myShowImmediately;
  }

  /**
   * Make sense if and only if isAwtTooltip set to {@code true}
   *
   * @param showImmediately true or false
   * @return current instance of HintHint
   */
  public HintHint setShowImmediately(boolean showImmediately) {
    myShowImmediately = showImmediately;
    return this;
  }

  public boolean isAnimationEnabled() {
    return myAnimationEnabled;
  }

  /**
   * @param enabled is {@code true} by default and balloon appears with transparency animation. {@code false} means instant opaque showing.
   * @return current instance of HintHint
   */
  public HintHint setAnimationEnabled(boolean enabled) {
    myAnimationEnabled = enabled;
    return this;
  }

  public boolean isRequestFocus() {
    return myRequestFocus;
  }

  public HintHint setRequestFocus(boolean requestFocus) {
    myRequestFocus = requestFocus;
    return this;
  }

  public enum Status {
    Info(Tooltip.FOREGROUND, Tooltip.BACKGROUND, Tooltip.BORDER, null),
    Success(Tooltip.FOREGROUND, Tooltip.SUCCESS_BACKGROUND, Tooltip.SUCCESS_BORDER, AllIcons.Debugger.ThreadStates.Idle),
    Warning(Tooltip.FOREGROUND, Tooltip.WARNING_BACKGROUND, Tooltip.WARNING_BORDER, AllIcons.General.BalloonWarning),
    Error(Tooltip.FOREGROUND, Tooltip.ERROR_BACKGROUND, Tooltip.ERROR_BORDER, AllIcons.General.BalloonError);

    public final Color foreground;
    public final Color background;
    public final Color border;
    final Icon icon;

    Status(@NotNull Color foreground, @NotNull Color background, @NotNull Color border, @Nullable Icon icon) {
      this.foreground = foreground;
      this.background = background;
      this.border = border;
      this.icon = icon;
    }
  }
}
