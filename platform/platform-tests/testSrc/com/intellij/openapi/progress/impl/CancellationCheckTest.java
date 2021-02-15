// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Clock;
import com.intellij.testFramework.LightPlatformTestCase;
import one.util.streamex.StreamEx;
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
    DefaultLogger.disableStderrDumping(getTestRootDisposable());

    CancellationCheck cancellation = new CancellationCheck(1);

    assertThrows(Throwable.class,
                 "AWT-EventQueue-0 last checkCanceled was ",
                 () -> {
                   runWithCheckCancellation(cancellation, 10, 1, 1);
                 });
  }


  public void testTraceLastCheck() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());

    CancellationCheck cancellation = new CancellationCheck(1000);

    try {
      cancellation.withCancellationCheck(() -> {
        Clock.setTime(1L);
        myProgressIndicator.checkCanceled();
        Clock.setTime(10L);
        myProgressIndicator.checkCanceled();
        Clock.setTime(1000L);
        myProgressIndicator.checkCanceled();
        Clock.setTime(10000L);
        myProgressIndicator.checkCanceled();
        return null;
      });
      fail("This code should not be executed");
    }
    catch (AssertionError e) {
      Throwable cancellationFailure = e.getCause();
      if(cancellationFailure == null) throw e;
      assertTrue(cancellationFailure.getMessage().startsWith("AWT-EventQueue-0 last checkCanceled was 9000 ms ago"));
      Throwable lastRecordedCheck = cancellationFailure.getCause();
      assertEquals("previous check cancellation call", lastRecordedCheck.getMessage());
      assertEquals("checkCancellationDiff, access$checkCancellationDiff, runHook, runCheckCanceledHooks, checkCanceled, checkCanceled",
                   StreamEx.of(lastRecordedCheck.getStackTrace())
                     .map(s -> s.getMethodName())
                     .without("lambda$createCheckCanceledHook$3")
                     .limit(6)
                     .joining(", "));
    }
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
