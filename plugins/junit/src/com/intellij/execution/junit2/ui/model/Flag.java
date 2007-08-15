package com.intellij.execution.junit2.ui.model;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.execution.ExecutionBundle;

class Flag {
  private final Logger myLogger;
  private boolean myValue;

  public Flag(final Logger logger, final boolean value) {
    myLogger = logger;
    myValue = value;
  }

  public void setValue(final boolean value) {
    myValue = value;
//    StringWriter out = new StringWriter();
//    new Exception(String.valueOf(value)).printStackTrace(new PrintWriter(out));
//    myLastAccess = out.toString();
  }

  public void assertValue(final boolean expected) {
    myLogger.assertTrue(expected == myValue, "first time");
  }

  public boolean getValue() {
    return myValue;
  }
}
