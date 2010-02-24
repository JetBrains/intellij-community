/*
 * User: anna
 * Date: 19-Feb-2010
 */
package com.intellij.coverage.listeners;

public abstract class CoverageListener {
  private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  private Object myProjectData;

  protected Object getData() {
    try {

     return Class.forName("com.intellij.rt.coverage.data.ProjectData").getMethod("getProjectData", EMPTY_CLASS_ARRAY).invoke(null);

    }
    catch (Exception e) {
      return null; //should not happen
    }
  }
}