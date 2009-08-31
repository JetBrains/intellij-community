/*
 * User: anna
 * Date: 28-Aug-2009
 */
package com.intellij.junit;

import com.intellij.junit4.JUnit4ReflectionUtil;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import org.junit.runner.Description;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"UnnecessaryFullyQualifiedName"})
public class IDEAJUnitCoverageListener extends IDEAJUnitListener {

  public void addError(final Test test, final Throwable t) {
  }

  public void addFailure(final Test test, final AssertionFailedError t) {
  }

  public void startTest(final Test test) {
    final Object data = getData();
    if (data != null && test instanceof TestCase) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testStarted(test.getClass().getName() + "." + ((TestCase)test).getName());

    }
  }

  public void endTest(final Test test) {
    final Object data = getData();
    if (data != null && test instanceof TestCase) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(test.getClass().getName() + "." + ((TestCase)test).getName());
    }
  }

  public void testStarted(Description description) {
    final Object data = getData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data)
        .testStarted(JUnit4ReflectionUtil.getClassName(description) + "." + JUnit4ReflectionUtil.getMethodName(description));
    }
  }

  public void testFinished(Description description) {
    final Object data = getData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data)
        .testEnded(JUnit4ReflectionUtil.getClassName(description) + "." + JUnit4ReflectionUtil.getMethodName(description));
    }
  }


  @Nullable
  private static Object getData() {
    try {
      return Class.forName("com.intellij.rt.coverage.data.ProjectData").getMethod("getProjectData", new Class[0]).invoke(null);
    }
    catch (Exception e) {
      return null;
    }
  }
}