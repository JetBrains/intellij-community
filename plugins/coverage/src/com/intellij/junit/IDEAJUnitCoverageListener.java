/*
 * User: anna
 * Date: 28-Aug-2009
 */
package com.intellij.junit;

import com.intellij.coverage.IDEACoverageRunner;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.util.ArrayUtil;

@SuppressWarnings({"UnnecessaryFullyQualifiedName"})
public class IDEAJUnitCoverageListener implements IDEAJUnitListener {

  public void testStarted(String className, String methodName) {
    final Object data = getData();
    ((com.intellij.rt.coverage.data.ProjectData)data).testStarted(className + "." + methodName);
  }

  public void testFinished(String className, String methodName) {
    final Object data = getData();
    ((com.intellij.rt.coverage.data.ProjectData)data).testEnded(className + "." + methodName);
  }

  public boolean isEnabled(Object configuration) {
    final CoverageEnabledConfiguration enabledConfiguration = CoverageEnabledConfiguration.get((ModuleBasedConfiguration)configuration);
    return enabledConfiguration.isCoverageEnabled() && enabledConfiguration.getCoverageRunner() instanceof IDEACoverageRunner;
  }

  private static Object getData() {
    try {
      return Class.forName("com.intellij.rt.coverage.data.ProjectData").getMethod("getProjectData", ArrayUtil.EMPTY_CLASS_ARRAY).invoke(null);
    }
    catch (Exception e) {
      return null;
    }
  }
}