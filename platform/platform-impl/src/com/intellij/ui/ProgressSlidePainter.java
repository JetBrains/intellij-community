// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ProgressSlide;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

final class ProgressSlidePainter {
  private static final int PREFETCH_BUFFER_SIZE = 5;
  private final BlockingQueue<Slide> myPrefetchQueue = new ArrayBlockingQueue<>(PREFETCH_BUFFER_SIZE);
  private boolean isFinish;
  private Slide nextSlide = null;
  private final List<ProgressSlide> myProgressSlides;

  private static class Slide {
    public final double progress;
    public final Image image;
    public final boolean isLastSlide;

    Slide(double progress, Image image, boolean isLastSlide) {
      this.progress = progress;
      this.image = image;
      this.isLastSlide = isLastSlide;
    }
  }

  ProgressSlidePainter(@NotNull ApplicationInfoEx appInfo) {
    myProgressSlides = appInfo.getProgressSlides();
    myProgressSlides.sort(Comparator.comparing(it -> it.progressRation));
  }

  public void startPreloading() {
    AppExecutorUtil.getAppExecutorService().execute(() -> {
      for (int i = 0; i < myProgressSlides.size(); i++) {
        ProgressSlide slide = myProgressSlides.get(i);
        try {
          Image image = ImageLoader.loadFromUrl(slide.url, Splash.class, ImageLoader.ALLOW_FLOAT_SCALING, null, ScaleContext.create());
          if (image == null) {
            throw new IllegalStateException("Cannot load slide by url: " + slide.url);
          }
          myPrefetchQueue.put(new Slide(slide.progressRation, image, i == myProgressSlides.size() - 1));
        }
        catch (InterruptedException e) {
          return;
        }
      }
    });
  }

  public void paintSlides(@NotNull Graphics g, double currentProgress) {
    if (isFinish || (nextSlide != null && nextSlide.progress > currentProgress)) {
      return;
    }

    if (nextSlide != null) {
      StartupUiUtil.drawImage(g, nextSlide.image, 0, 0, null);
      if (nextSlide.isLastSlide) {
        isFinish = true;
        nextSlide = null;
        return;
      }
    }

    Slide newSlide;
    do {
      try {
        newSlide = myPrefetchQueue.take();
      }
      catch (InterruptedException e) {
        return;
      }
      if (newSlide.progress <= currentProgress) {
        StartupUiUtil.drawImage(g, newSlide.image, 0, 0, null);
        if (newSlide.isLastSlide) {
          nextSlide = null;
          isFinish = true;
        }
      }
      else {
        nextSlide = newSlide;
        break;
      }
    }
    while (!newSlide.isLastSlide);
  }
}
