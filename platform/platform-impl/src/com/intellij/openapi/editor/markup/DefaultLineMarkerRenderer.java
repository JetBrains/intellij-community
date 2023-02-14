// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class DefaultLineMarkerRenderer implements LineMarkerRendererEx {
  private final TextAttributesKey myAttributesKey;
  private final int myThickness;
  private final int myDepth;
  private final Position myPosition;

  private final Color myColor;

  public DefaultLineMarkerRenderer(@NotNull TextAttributesKey myAttributesKey, int thickness) {
    this(myAttributesKey, thickness, 0, Position.RIGHT);
  }

  public DefaultLineMarkerRenderer(@NotNull TextAttributesKey attributesKey, int thickness, int depth, @NotNull Position position) {
    myAttributesKey = attributesKey;
    myThickness = thickness;
    myDepth = depth;
    myPosition = position;

    EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
    TextAttributes attributes = scheme.getAttributes(myAttributesKey);
    Color color = attributes.getBackgroundColor();
    color = color != null ? color : attributes.getForegroundColor();

    if (color != null) {
      myColor = ColorUtil.isDark(scheme.getDefaultBackground()) ? ColorUtil.shift(color, 1.5d) : color.darker();
    }
    else {
      myColor = null;
    }
  }

  @Override
  public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r) {
    if (myColor == null || ExperimentalUI.isNewUI()) return;

    g.setColor(myColor);
    g.fillRect(r.x, r.y, myThickness, r.height);
    g.fillRect(r.x + myThickness, r.y, myDepth, myThickness);
    g.fillRect(r.x + myThickness, r.y + r.height - myThickness, myDepth, myThickness);
  }

  public @NotNull TextAttributesKey getAttributesKey() {
    return myAttributesKey;
  }

  public int getDepth() {
    return myDepth;
  }

  public int getThickness() {
    return myThickness;
  }

  @Override
  public @NotNull Position getPosition() {
    return myPosition;
  }

  public Color getColor() {
    return myColor;
  }
}
