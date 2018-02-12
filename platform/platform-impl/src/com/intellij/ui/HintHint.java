/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.ide.IdeTooltipManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class HintHint {

  private Component myOriginalComponent;
  private Point myOriginalPoint;

  private boolean myAwtTooltip = false;
  private Balloon.Position myPreferredPosition = Balloon.Position.below;

  private boolean myContentActive = true;

  private boolean myQuickHint = false;
  private boolean myMayCenterTooltip = false;

  private Color myTextFg;
  private Color myTextBg;
  private Color myBorderColor;
  private Insets myBorderInsets;
  private Font myFont;
  private int myCalloutShift;

  private boolean myExplicitClose;
  private int myPositionChangeX;
  private int myPositionChangeY;
  private boolean myShowImmediately = false;
  private boolean myAnimationEnabled;
  private boolean myRequestFocus;

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

  public Component getOriginalComponent() {
    return myOriginalComponent;
  }

  public Point getOriginalPoint() {
    return myOriginalPoint;
  }

  public RelativePoint getTargetPoint() {
    return new RelativePoint(getOriginalComponent(), getOriginalPoint());
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

  public Color getBorderColor() {
    return myBorderColor != null ? myBorderColor : getTooltipManager().getBorderColor(myAwtTooltip);
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

  private IdeTooltipManager getTooltipManager() {
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
    if (c instanceof JComponent) {
      JComponent jc = (JComponent)c;
      jc.setOpaque(isOpaqueAllowed());
      jc.setBorder(isOwnBorderAllowed() ? BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.black), BorderFactory.createEmptyBorder(0, 5, 0, 5)) : null);
    }
  }

  public void initStyleFrom(JComponent component) {
    setTextFg(component.getForeground()).setTextBg(component.getBackground()).setFont(component.getFont());
    myTextFg = component.getForeground();
    myTextBg = component.getBackground();
    myFont = component.getFont();
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
   *
   * @param enabled is {@code true} by default and balloon appears with transparency animation. {@code false} means instant opaque showing.
   * @return current instance of HintHint
   */
  public HintHint setAnimationEnabled(boolean enabled){
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
}
