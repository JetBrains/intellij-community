// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.listeners.java;

public abstract class CoverageListener {
  private static final Class[] EMPTY_CLASS_ARRAY = new Class[0];
  private Object myProjectData;

  protected static String sanitize(String className, String methodName) {
    return className + "," + sanitize(methodName, className.length());
  }

  public static String sanitize(String name, int length) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      final char ch = name.charAt(i);

      if (ch > 0 && ch < 255) {
        if (Character.isJavaIdentifierPart(ch) || ch == ' ' || ch == '@' || ch == '-') {
          result.append(ch);
        }
        else {
          result.append("_");
        }
      }

    }

    int methodNameLimit = 250 - length;
    if (result.length() >= methodNameLimit) {
      String hash = String.valueOf(result.toString().hashCode());
      return (methodNameLimit > hash.length() ? result.substring(0, methodNameLimit - hash.length()) : "") + hash;
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
