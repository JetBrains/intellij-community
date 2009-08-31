/*
 * User: anna
 * Date: 31-Aug-2009
 */
package com.intellij.rt.execution.junit;

import junit.framework.TestListener;
import org.junit.runner.notification.RunListener;

public abstract class IDEAJUnitListener extends RunListener implements TestListener {
  public static final String EP_NAME = "com.intellij.junitListener";
}