// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.util.ArrayUtil;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestListener;

import java.lang.reflect.Method;

@SuppressWarnings({"unused", "UseOfSystemOutOrSystemErr", "CallToPrintStackTrace"})
public class TestDiscoveryBasicListener implements TestListener {
  @Override
  public void addError(Test test, Throwable t) {}

  @Override
  public void addFailure(Test test, AssertionFailedError t) {}

  @Override
  public void endTest(Test test) {
    String className = getClassName(test);
    String methodName = getMethodName(test);

    try {
      Object data = getData();
      Method testEnded = data.getClass().getMethod("testDiscoveryEnded", String.class, String.class);
      testEnded.invoke(data, className, methodName);
    }
    catch (Throwable t) {
      t.printStackTrace();
    }
  }

  @Override
  public void startTest(Test test) {
    try {
      Object data = getData();
      Method testStarted = data.getClass().getMethod("testDiscoveryStarted", String.class, String.class);
      testStarted.invoke(data, getClassName(test), getMethodName(test));
    }
    catch (Throwable t) {
      t.printStackTrace();
    }
  }

  protected Object getData() throws Exception {
    return Class.forName("com.intellij.rt.coverage.data.TestDiscoveryProjectData")
                .getMethod("getProjectData", ArrayUtil.EMPTY_CLASS_ARRAY)
                .invoke(null, ArrayUtil.EMPTY_OBJECT_ARRAY);
  }

  private static String getMethodName(Test test) {
    String toString = test.toString();
    int braceIdx = toString.indexOf("(");
    return braceIdx > 0 ? toString.substring(0, braceIdx) : toString;
  }

  private static String getClassName(Test test) {
    String toString = test.toString();
    int braceIdx = toString.indexOf("(");
    return braceIdx > 0 && toString.endsWith(")") ? toString.substring(braceIdx + 1, toString.length() - 1) : null;
  }
}
