// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.listeners.java.testng;

import com.intellij.coverage.listeners.java.CoverageListener;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.testng.IDEATestNGListener;
import org.testng.ITestContext;
import org.testng.ITestResult;

public class IDEATestNGCoverageListener extends CoverageListener implements IDEATestNGListener {

  @Override
  public void onTestStart(final ITestResult iTestResult) {
    final Object data = getData();
    if (data != null) {
      ((ProjectData)data).testStarted(getSanitizeTestName(iTestResult));
    }
  }

  private static String getSanitizeTestName(ITestResult iTestResult) {
    return sanitize(iTestResult.getTestClass().getName(), iTestResult.getName());
  }

  @Override
  public void onTestSuccess(final ITestResult iTestResult) {
    final Object data = getData();
    if (data != null) {
      ((ProjectData)data).testEnded(getSanitizeTestName(iTestResult));
    }
  }

  @Override
  public void onTestFailure(final ITestResult iTestResult) {
    final Object data = getData();
    if (data != null) {
      ((ProjectData)data).testEnded(getSanitizeTestName(iTestResult));
    }
  }

  @Override
  public void onTestSkipped(final ITestResult iTestResult) {
    final Object data = getData();
    if (data != null) {
      ((ProjectData)data).testEnded(getSanitizeTestName(iTestResult));
    }
  }

  @Override
  public void onTestFailedButWithinSuccessPercentage(final ITestResult iTestResult) {

  }

  @Override
  public void onStart(final ITestContext iTestContext) {
  }

  @Override
  public void onFinish(final ITestContext iTestContext) {
  }

}
