// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.application.ex.ProgressSlide;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class ProgressSlidePainter {
  private static final int PREFETCH_PARALLEL_COUNT = 4;
  private static final int PREFETCH_BUFFER_SIZE = 15;

  private final List<ProgressSlide> myProgressSlides;

  private final AtomicReferenceArray<Slide> myPrefetchedSlides;
  private final AtomicInteger myPrefetchSlideIndex;
  private volatile int myNextSlideIndex = 0;

  private static final class Slide {
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

  public void startPreloading() {
    var executorService = AppExecutorUtil.getAppExecutorService();
    for (int i = 0; i < PREFETCH_PARALLEL_COUNT; i++) {
      executorService.execute(() -> {
        while (true) {
          int slideIndex = myPrefetchSlideIndex.getAndIncrement();
          if (slideIndex >= myProgressSlides.size()) return;

          while (slideIndex > myNextSlideIndex + PREFETCH_BUFFER_SIZE)
            Thread.onSpinWait();

          var slide = myProgressSlides.get(slideIndex);
          var image = Splash.doLoadImage(slide.url, JBUIScale.sysScale());
          if (image == null) {
            Logger.getInstance(ProgressSlidePainter.class).error("Cannot load slide by url: " + slide.url);
            myPrefetchedSlides.set(slideIndex, Slide.Empty);
            continue;
          }
          myPrefetchedSlides.compareAndSet(slideIndex, null, new Slide(slide.progressRation, image));
        }
      });
    }
  }

  public void paintSlides(@NotNull Graphics g, double currentProgress) {
    do {
      int index = myNextSlideIndex;
      if (index >= myPrefetchedSlides.length())
        return;

      var newSlide = myPrefetchedSlides.get(index);
      if (newSlide == null || newSlide.isEmpty()) {
        if (myProgressSlides.get(index).progressRation <= currentProgress) {
          next(index);
          continue;
        }
        return;
      }

      if (newSlide.progress <= currentProgress) {
        StartupUiUtil.drawImage(g, newSlide.image, 0, 0, null);
        next(index);
      } else return;
    }
    while (true);
  }

  private void next(int index) {
    myPrefetchedSlides.set(index, Slide.Empty);
    //noinspection NonAtomicOperationOnVolatileField
    myNextSlideIndex++;
  }
}
