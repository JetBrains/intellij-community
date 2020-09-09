// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ProgressSlide;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ImageLoader;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class ProgressSlidePainter {
  private static final int PREFETCH_PARALLEL_COUNT = 5;
  private static final int PREFETCH_BUFFER_SIZE = 32;
  private static final Logger ourLogger = Logger.getInstance(ProgressSlidePainter.class);

  private final List<ProgressSlide> myProgressSlides;

  private final AtomicReferenceArray<Slide> myPrefetchedSlides;
  private final AtomicInteger myPrefetchSlideIndex;
  private volatile int myNextSlideIndex = 0;

  private static class Slide {
    private static final Slide Empty = new Slide(0, null);

    public final double progress;
    public final Image image;

    public boolean isEmpty() {
      return Empty.equals(this);
    }

    Slide(double progress, Image image) {
      this.progress = progress;
      this.image = image;
    }
  }

  ProgressSlidePainter(@NotNull ApplicationInfoEx appInfo) {
    myProgressSlides = appInfo.getProgressSlides();
    myProgressSlides.sort(Comparator.comparing(it -> it.progressRation));
    myPrefetchedSlides = new AtomicReferenceArray<>(myProgressSlides.size());
    myPrefetchSlideIndex = new AtomicInteger(0);
  }

  private boolean isFinished() {
    return myNextSlideIndex >= myPrefetchedSlides.length();
  }

  public void startPreloading() {
    var executorService = AppExecutorUtil.getAppExecutorService();
    for (int i = 0; i < PREFETCH_PARALLEL_COUNT; i++) {
      executorService.execute(() -> {
        while (true) {
          int slideIndex = myPrefetchSlideIndex.getAndIncrement();
          if (slideIndex >= myProgressSlides.size()) return;

          if (slideIndex < myNextSlideIndex)
          {
            // current slide will not be shown
            myPrefetchedSlides.set(slideIndex, Slide.Empty);
            continue;
          }

          while (slideIndex > myNextSlideIndex + PREFETCH_BUFFER_SIZE)
            Thread.onSpinWait();

          var slide = myProgressSlides.get(slideIndex);
          var image = ImageLoader.loadFromUrl(slide.url, Splash.class, ImageLoader.ALLOW_FLOAT_SCALING, null, ScaleContext.create());

          if (image == null) {
            ourLogger.error("Cannot load slide by url: " + slide.url);
            myPrefetchedSlides.set(slideIndex, Slide.Empty);
            continue;
          }

          myPrefetchedSlides.compareAndSet(slideIndex, null, new Slide(slide.progressRation, image));
        }
      });
    }
  }

  public void paintSlides(@NotNull Graphics g, double currentProgress) {
    if (isFinished()) return;

    do {
      var newSlide = tryGetNextSlide();
      if (newSlide == null || newSlide.isEmpty()) return;

      if (newSlide.progress <= currentProgress) {
        StartupUiUtil.drawImage(g, newSlide.image, 0, 0, null);
      } else return;
    }
    while (true);
  }

  // on ui thread
  @Nullable
  private Slide tryGetNextSlide() {
    int index = myNextSlideIndex;
    if (index >= myPrefetchedSlides.length()) return null;

    var slide = myPrefetchedSlides.get(index);
    myPrefetchedSlides.set(index, Slide.Empty);
    //noinspection NonAtomicOperationOnVolatileField
    myNextSlideIndex++;
    return slide;
  }
}
