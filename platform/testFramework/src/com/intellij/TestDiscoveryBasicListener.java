// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij;

import com.intellij.util.ArrayUtil;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import org.junit.runner.Describable;
import org.junit.runner.Description;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    if (test instanceof TestCase) {
      String name = ((TestCase)test).getName();
      if (name != null) return name;
    }

    if (test instanceof Describable) {
      Description description = ((Describable)test).getDescription();
      String name = getMethodName(description);
      if (name != null) return name;
    }

    String toString = test.toString();
    int braceIdx = toString.indexOf("(");
    return braceIdx > 0 ? toString.substring(0, braceIdx) : toString;
  }

  private static String getClassName(Test test) {
    if (test instanceof Describable) {
      Description description = ((Describable)test).getDescription();
      String name = getClassName(description);
      if (name != null) return name;
    }
    return test.getClass().getName();
  }

  public static String getClassName(Description description) {
    try {
      return description.getClassName();
    }
    catch (NoSuchMethodError e) {
      final String displayName = description.getDisplayName();
      Matcher matcher = Pattern.compile("(.*)\\((.*)\\)").matcher(displayName);
      return matcher.matches() ? matcher.group(2) : displayName;
    }
  }

  public static String getMethodName(Description description) {
    try {
      return description.getMethodName();
    }
    catch (NoSuchMethodError e) {
      final String displayName = description.getDisplayName();
      Matcher matcher = Pattern.compile("(.*)\\((.*)\\)").matcher(displayName);
      if (matcher.matches()) return matcher.group(1);
      return null;
    }
  }
}
