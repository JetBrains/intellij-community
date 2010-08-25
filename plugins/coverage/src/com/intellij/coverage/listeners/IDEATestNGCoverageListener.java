/*
 * User: anna
 * Date: 29-May-2008
 */
package com.intellij.coverage.listeners;

import org.testng.IDEATestNGListener;
import org.testng.ITestContext;
import org.testng.ITestResult;

public class IDEATestNGCoverageListener extends CoverageListener implements IDEATestNGListener {

  public void onTestStart(final ITestResult iTestResult) {
    final Object data = getData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testStarted(getSanitizeTestName(iTestResult));
    }
  }

  private static String getSanitizeTestName(ITestResult iTestResult) {
    return sanitize(iTestResult.getTestClass().getName(), iTestResult.getName());
  }

  public void onTestSuccess(final ITestResult iTestResult) {
    final Object data = getData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(getSanitizeTestName(iTestResult));
    }
  }

  public void onTestFailure(final ITestResult iTestResult) {
    final Object data = getData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(getSanitizeTestName(iTestResult));
    }
  }

  public void onTestSkipped(final ITestResult iTestResult) {
    final Object data = getData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(getSanitizeTestName(iTestResult));
    }
  }

  public void onTestFailedButWithinSuccessPercentage(final ITestResult iTestResult) {

  }

  public void onStart(final ITestContext iTestContext) {
  }

  public void onFinish(final ITestContext iTestContext) {
  }

}
