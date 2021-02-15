// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
*/
@SuppressWarnings({"UnusedDeclaration", "SSBasedInspection"})
public class TimedOutCallback extends ActionCallback implements Runnable {
  private static final Logger LOG = Logger.getInstance(TimedOutCallback.class);

  private Throwable myAllocation;
  private String myMessage;
  private SimpleTimerTask myTask;
  private boolean myShouldDumpError;

  public TimedOutCallback(final long timeOut, String message, Throwable allocation, boolean isEdt) {
    scheduleCheck(timeOut, message, allocation, isEdt);
  }

  public TimedOutCallback(int countToDone, long timeOut, String message, Throwable allocation, boolean isEdt) {
    super(countToDone);
    scheduleCheck(timeOut, message, allocation, isEdt);
  }

  private void scheduleCheck(final long timeOut, final String message, Throwable allocation, final boolean isEdt) {
    myMessage = message;
    myAllocation = allocation;
    final long current = System.currentTimeMillis();
    myTask = SimpleTimer.getInstance().setUp(() -> {
      myShouldDumpError = System.currentTimeMillis() - current > timeOut; //double check is necessary :-(
      if (isEdt) {
        SwingUtilities.invokeLater(this);
      } else {
        this.run();
      }
    }, timeOut);
  }

  @Override
  public final void run() {
    if (!isProcessed()) {
      setRejected();

      if (myShouldDumpError) {
        dumpError();
      }

      onTimeout();
    }
  }

  protected void dumpError() {
    if (myAllocation != null) {
      LOG.error(myMessage, myAllocation);
    } else {
      LOG.error(myMessage);
    }
  }

  public String getMessage() {
    return myMessage;
  }

  public Throwable getAllocation() {
    return myAllocation;
  }

  @Override
  public void dispose() {
    super.dispose();
    myTask.cancel();
  }

  protected void onTimeout() {
  }
}
