/*
 * User: anna
 * Date: 29-May-2008
 */
package org.testng;

public class IDEACoverageListener implements IDEATestNGListener {

  public void onTestStart(final ITestResult iTestResult) {
    final Object data = getProjectData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testStarted(iTestResult.getTestClass().getName() + "." + iTestResult.getName());
    }
  }

  private static Object getProjectData() {
    try {
      return Class.forName("com.intellij.rt.coverage.data.ProjectData").getMethod("getProjectData", new Class[0])
        .invoke(null, new Object[0]);
    }
    catch (Exception e) {
      return null;
    }
  }

  public void onTestSuccess(final ITestResult iTestResult) {
    final Object data = getProjectData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(iTestResult.getTestClass().getName() + "." + iTestResult.getName());
    }
  }

  public void onTestFailure(final ITestResult iTestResult) {
    final Object data = getProjectData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(iTestResult.getTestClass().getName() + "." + iTestResult.getName());
    }
  }

  public void onTestSkipped(final ITestResult iTestResult) {
    final Object data = getProjectData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(iTestResult.getTestClass().getName() + "." + iTestResult.getName());
    }
  }

  public void onTestFailedButWithinSuccessPercentage(final ITestResult iTestResult) {

  }

  public void onStart(final ITestContext iTestContext) {
  }

  public void onFinish(final ITestContext iTestContext) {
  }
}
