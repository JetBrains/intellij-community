// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

public class ContextLogger {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.diff.impl.util.ContextLogger");
  private final Logger myLogger;
  private final Context myInfo;
  private boolean myFirstError = true;

  public ContextLogger(Logger logger, Object context) {
    this(logger, new SimpleContext(context));
  }

  public ContextLogger(Logger logger, Context context) {
    myLogger = logger;
    myInfo = context;
  }

  public ContextLogger(String info) {
    this(LOG, info);
    assertTrue(isTestMode());
  }

  public void assertTrue(boolean condition) {
    assertTrue(condition, "");
  }

  public void assertTrue(boolean condition, String message) {
    if (condition) return;
    logError("Assertion: " + message);
  }

  private void logError(@NonNls String message) {
    if (myFirstError) {
      myLogger.error(message, myInfo.getDetails());
      myFirstError = false;
    } else myLogger.error(message);
  }

  private boolean isTestMode() {
    Application application = ApplicationManager.getApplication();
    return application == null || application.isUnitTestMode();
  }

  public void notImplemented() {
    throwTestException("Not implemented");
  }

  private void throwTestException(@NonNls String message) {
    if (isTestMode()) throw new RuntimeException(message);
    else logError(message);
  }

  public void notTested() {
    throwTestException("Not Tested");
  }

  public void error(String message) {
    logError(message);
  }

  public interface Context {
    String[] getDetails();
  }

  public static class SimpleContext implements Context {
    private final Object[] myContext;

    public SimpleContext(Object obj) {
      this(new Object[]{obj});
    }

    public SimpleContext(Object[] data) {
      myContext = data;
    }

    @Override
    public String[] getDetails() {
      String[] result = new String[myContext.length];
      for (int i = 0; i < myContext.length; i++) {
        Object object = myContext[i];
        result[i] = String.valueOf(object);
      }
      return result;
    }
  }
}
