// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class GutterIconDropAnimator extends AbstractPainter {
  private final Point myExplosionLocation;
  private final Image myImage;
  private final @NotNull Disposable myPainterListenersDisposable;

  private static final long TIME_PER_FRAME = 30;
  private final int myWidth;
  private final int myHeight;
  private long lastRepaintTime = System.currentTimeMillis();
  private int frameIndex;
  private static final int TOTAL_FRAMES = 8;

  private final AtomicBoolean nrp = new AtomicBoolean(true);

  GutterIconDropAnimator(final Point explosionLocation, Image image, @NotNull Disposable painterListenersDisposable) {
    myExplosionLocation = new Point(explosionLocation.x, explosionLocation.y);
    myImage = image;
    myPainterListenersDisposable = painterListenersDisposable;
    myWidth = myImage.getWidth(null);
    myHeight = myImage.getHeight(null);
  }

  @Override
  public void executePaint(Component component, Graphics2D g) {
    if (!nrp.get()) return;

    long currentTimeMillis = System.currentTimeMillis();

    float alpha = 1 - (float)frameIndex / TOTAL_FRAMES;
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
    Image scaledImage = ImageUtil.scaleImage(myImage, alpha);

    int x = myExplosionLocation.x - scaledImage.getWidth(null) / 2;
    int y = myExplosionLocation.y - scaledImage.getHeight(null) / 2;

    if (currentTimeMillis - lastRepaintTime < TIME_PER_FRAME) {
      UIUtil.drawImage(g, scaledImage, x, y, null);
      EdtExecutorService.getScheduledExecutorInstance().schedule(() -> component.repaint(x, y, myWidth, myHeight),
                                                                 TIME_PER_FRAME, TimeUnit.MILLISECONDS);
      return;
    }
    lastRepaintTime = currentTimeMillis;
    frameIndex++;
    UIUtil.drawImage(g, scaledImage, x, y, null);
    if (frameIndex == TOTAL_FRAMES) {
      nrp.set(false);
      ApplicationManager.getApplication().invokeLater(() -> Disposer.dispose(myPainterListenersDisposable));
      component.repaint(x, y, myWidth, myHeight);
    }
    component.repaint(x, y, myWidth, myHeight);
  }

  @Override
  public boolean needsRepaint() {
    return nrp.get();
  }

}
