// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public final class GraphicsConfig {
  private final Graphics2D myG;
  private final Map myHints;
  private final Composite myComposite;
  private final Stroke myStroke;

  public GraphicsConfig(@NotNull Graphics g) {
    myG = (Graphics2D)g;
    myHints = (Map)myG.getRenderingHints().clone();
    myComposite = myG.getComposite();
    myStroke = myG.getStroke();
  }

  public GraphicsConfig setAntialiasing(boolean on) {
    myG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, on ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
    return this;
  }

  public GraphicsConfig setAlpha(float alpha) {
    myG.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    return this;
  }

  public GraphicsConfig setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
    myG.setRenderingHint(hintKey, hintValue);
    return this;
  }

  public Graphics2D getG() {
    return myG;
  }

  public GraphicsConfig setComposite(Composite composite) {
    myG.setComposite(composite);
    return this;
  }

  public GraphicsConfig setStroke(Stroke stroke) {
    myG.setStroke(stroke);
    return this;
  }

  public GraphicsConfig setupRoundedBorderAntialiasing() {
    return setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      .setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      .setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));
  }

  public GraphicsConfig setupAAPainting() {
    return setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      .setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
  }

  public GraphicsConfig disableAAPainting() {
    return setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
      .setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
  }

  public GraphicsConfig paintWithAlpha(float alpha) {
    assert 0.0f <= alpha && alpha <= 1.0f : "alpha should be in range 0.0f .. 1.0f";
    return setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
  }

  public void restore() {
    myG.setRenderingHints(myHints);
    myG.setComposite(myComposite);
    myG.setStroke(myStroke);
  }
}
