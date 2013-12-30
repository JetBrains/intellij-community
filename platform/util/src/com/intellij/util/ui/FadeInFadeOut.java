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
package com.intellij.util.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * It supposed to cover whole area of JLayeredPane and process "FadeIn&Out" effects with its inner components
 */

public class FadeInFadeOut extends JComponent {
  public static final Integer LAYER = new Integer(JLayeredPane.POPUP_LAYER + 1);

  @NotNull
  private final Component myComponent;
  private final BufferedImage myComponentImage;
  private Rectangle myComponentBounds;

  @NotNull
  private final Component myIcon;
  private Rectangle myIconBounds;

  private final boolean myFadeIn;

  private boolean myTriggered = false;
  private final int myTimeToComplete;
  private double myRatio = 0;

  public FadeInFadeOut(@NotNull Component component, int timeToComplete, boolean fadeIn, @NotNull Component icon) {
    setFocusable(false);
    myComponent = component;
    myTimeToComplete = timeToComplete;
    myFadeIn = fadeIn;
    myRatio = myFadeIn ? 0 : 1;
    myIcon = icon;

    myComponentImage = UIUtil.createImage(myComponent.getWidth(), myComponent.getHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = myComponentImage.createGraphics();
    myComponent.paint(graphics);
    graphics.dispose();
  }

  @Override
  public void reshape(int x, int y, int w, int h) {
    super.reshape(x, y, w, h);
    if (!myTriggered) {
      myComponentBounds = SwingUtilities.convertRectangle(myComponent.getParent(), myComponent.getBounds(), this);
      myIconBounds = SwingUtilities.convertRectangle(myIcon.getParent(), myIcon.getBounds(), this);
    }
  }

  public void doAnimation() {
    if (myTriggered || !isShowing()) return;

    myTriggered = true;
    long startTime = System.currentTimeMillis();
    while (true) {
      paintImmediately(0, 0, getWidth(), getHeight());
      final long timeSpent = System.currentTimeMillis() - startTime;
      if (timeSpent >= myTimeToComplete) {
        return;
      }
      double linearProgress = Math.max(0, Math.min(1, (double)timeSpent / myTimeToComplete));
      if (!myFadeIn) linearProgress = 1 - linearProgress;
      myRatio = (1 - Math.cos(Math.PI * linearProgress)) / 2;
    }
  }

  public final void paint(final Graphics g_) {
    long l = System.currentTimeMillis();
    int x = (int)(myIconBounds.x * (1 - myRatio) + myComponentBounds.x * myRatio);
    int y = (int)(myIconBounds.y * (1 - myRatio) + myComponentBounds.y * myRatio);
    int width = (int)(myIconBounds.width * (1 - myRatio) + myComponentBounds.width * myRatio);
    int height = (int)(myIconBounds.height * (1 - myRatio) + myComponentBounds.height * myRatio);
    Graphics2D g = (Graphics2D)g_.create();
    try {
      g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .5F + (float)(myRatio / 2)));
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
      g.drawImage(myComponentImage, x, y, width, height, this);
    }
    finally {
      g.dispose();
    }
  }
}
