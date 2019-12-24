// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Clock;
import com.intellij.testFramework.LightPlatformTestCase;
import org.junit.After;

public class CancellationCheckTest extends LightPlatformTestCase {

  private final ProgressIndicator myProgressIndicator = new ProgressIndicatorBase();

  @After
  public void reset() {
    Clock.reset();
  }

  public void testNormal() {
    int period = 10;
    int times = 10;
    CancellationCheck cancellation = new CancellationCheck(times * period);

    runWithCheckCancellation(cancellation, period, times, 1);
  }

  public void testReentrant() {
    int period = 10;
    int times = 5;
    CancellationCheck cancellation = new CancellationCheck(times * period);

    runWithCheckCancellation(cancellation, period, times, 3);
  }

  public void testExceededThreshold() {
    CancellationCheck cancellation = new CancellationCheck(1);

    assertThrows(Throwable.class,
                 "AWT-EventQueue-0 last checkCanceled was ",
                 () -> {
                   runWithCheckCancellation(cancellation, 10, 1, 1);
                 });
  }

  private void runWithCheckCancellation(CancellationCheck cancellation, int period, int times, int depth) {
    Clock.setTime(1L);
    cancellation.withCancellationCheck(() -> {
      for (int attempt = 0; attempt < times * 2; attempt++) {
        myProgressIndicator.checkCanceled();
        Clock.setTime(Clock.getTime() + period);
        if (depth - 1 > 0) {
          runWithCheckCancellation(cancellation, period, times, depth - 1);
        }
      }
      return null;
    });
  }

}
