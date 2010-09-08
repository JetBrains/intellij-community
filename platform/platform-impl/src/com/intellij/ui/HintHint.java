/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.awt.*;
import java.awt.event.MouseEvent;

public class HintHint {

  private Component myOriginalComponent;
  private Point myOriginalPoint;

  private boolean myAwtTooltip = false;
  private Balloon.Position myPreferredPosition = Balloon.Position.below;

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
    return getTooltipManager().getTextForeground(myAwtTooltip);
  }

  public Color getTextBackground() {
    return getTooltipManager().getTextBackground(myAwtTooltip);
  }

  public boolean isOwnBorderAllowed() {
    return getTooltipManager().isOwnBorderAllowed(myAwtTooltip);
  }

  public Color getBorderColor() {
    return getTooltipManager().getBorderColor(myAwtTooltip);
  }

  public boolean isOpaqueAllowed() {
    return getTooltipManager().isOpaqueAllowed(myAwtTooltip);
  }

  public Font getTextFont() {
    return getTooltipManager().getTextFont(myAwtTooltip);
  }

  private IdeTooltipManager getTooltipManager() {
    return IdeTooltipManager.getInstance();
  }
}
