/*
 * User: anna
 * Date: 28-Aug-2009
 */
package com.intellij.coverage.listeners;

import com.intellij.coverage.listeners.CoverageListener;
import com.intellij.rt.execution.junit.IDEAJUnitListener;

@SuppressWarnings({"UnnecessaryFullyQualifiedName"})
public class IDEAJUnitCoverageListener extends CoverageListener implements IDEAJUnitListener {

  public void testStarted(String className, String methodName) {
    final Object data = getData();
    ((com.intellij.rt.coverage.data.ProjectData)data).testStarted(className + "." + methodName);
  }

  public void testFinished(String className, String methodName) {
    final Object data = getData();
    ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(className + "." + methodName);
  }
}