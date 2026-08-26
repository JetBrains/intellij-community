// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tests;

import com.intellij.util.containers.ContainerUtil;
import org.junit.AssumptionViolatedException;
import org.opentest4j.TestAbortedException;

import java.lang.reflect.Method;
import java.util.List;

public class IgnoreException extends Exception {
  public static final IgnoreException INSTANCE = new IgnoreException();
  private static final String MULTIPLE_FAILURES_ERROR = "org.opentest4j.MultipleFailuresError";

  private IgnoreException() { }

  public static boolean isIgnoringThrowable(Throwable throwable) {
    if (throwable instanceof AssumptionViolatedException) return true;
    if (throwable instanceof TestAbortedException) return true;
    if (throwable instanceof IgnoreException) return true;
    if (throwable.getClass().getName().equals(MULTIPLE_FAILURES_ERROR)) {
      try {
        Method getFailuresMethod = Class.forName(MULTIPLE_FAILURES_ERROR).getDeclaredMethod("getFailures");
        @SuppressWarnings("unchecked") List<Throwable> failures = (List<Throwable>)getFailuresMethod.invoke(throwable);
        return ContainerUtil.and(failures, IgnoreException::isIgnoringThrowable);
      }
      catch (Throwable e) {
        return false;
      }
    }
    return false;
  }
}
