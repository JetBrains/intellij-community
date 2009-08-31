/*
 * User: anna
 * Date: 28-Aug-2009
 */
package com.intellij.junit;

import com.intellij.rt.execution.junit.IDEAJUnitListener;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"UnnecessaryFullyQualifiedName"})
public class IDEAJUnitCoverageListener implements IDEAJUnitListener {

  public void testStarted(String className, String methodName) {
    final Object data = getData();
    if (data != null ) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testStarted(className + "." + methodName);
    }
  }

  public void testFinished(String className, String methodName) {
    final Object data = getData();
    if (data != null) {
      ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(className + "." + methodName);
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