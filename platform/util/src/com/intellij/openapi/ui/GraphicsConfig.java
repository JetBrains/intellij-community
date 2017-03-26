/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.ui;

import java.awt.*;
import java.util.Map;

/**
 * @author Konstantin Bulenkov
 */
public class GraphicsConfig {
  private final Graphics2D myG;
  private final Map myHints;
  private final Composite myComposite;
  private final Stroke myStroke;

  public GraphicsConfig(Graphics g) {
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
