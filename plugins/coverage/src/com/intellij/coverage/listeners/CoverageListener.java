/*
 * User: anna
 * Date: 19-Feb-2010
 */
package com.intellij.coverage.listeners;

public abstract class CoverageListener {
  private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  private Object myProjectData;

  protected static String sanitize(String className, String methodName) {
    final StringBuilder result = new StringBuilder();
    final String fileName = className + "." + methodName;
    for (int i = 0; i < fileName.length(); i++) {
      final char ch = fileName.charAt(i);

      if (ch > 0 && ch < 255) {
        if (Character.isLetterOrDigit(ch)) {
          result.append(ch);
        }
        else {
          result.append("_");
        }
      }

    }

    return result.toString();
  }

  protected Object getData() {
    try {

     return Class.forName("com.intellij.rt.coverage.data.ProjectData").getMethod("getProjectData", EMPTY_CLASS_ARRAY).invoke(null);

    }
    catch (Exception e) {
      return null; //should not happen
    }
  }
}
