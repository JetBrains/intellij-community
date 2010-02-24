/*
 * User: anna
 * Date: 19-Feb-2010
 */
package com.intellij.coverage.listeners;

public abstract class CoverageListener {
  private Object myProjectData;

  protected Object getData() {
    try {

     return Class.forName("com.intellij.rt.coverage.data.ProjectData").getMethod("getProjectData", new Class[0]).invoke(null);

    }
    catch (Exception e) {
      return null; //should not happen
    }
  }
}