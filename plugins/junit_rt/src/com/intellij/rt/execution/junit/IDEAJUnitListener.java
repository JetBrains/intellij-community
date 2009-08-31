/*
 * User: anna
 * Date: 31-Aug-2009
 */
package com.intellij.rt.execution.junit;

public interface IDEAJUnitListener {
  String EP_NAME = "com.intellij.junitListener";

  void testStarted(String className, String methodName);
  void testFinished(String className, String methodName);

}