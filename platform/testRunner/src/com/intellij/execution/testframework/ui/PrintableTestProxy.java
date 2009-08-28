package com.intellij.execution.testframework.ui;

import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.ChangingPrintable;

/**
 * @author Roman Chernyatchik
 */
public interface PrintableTestProxy extends AbstractTestProxy, ChangingPrintable {
  String NEW_LINE = "\n";

  boolean isRoot();
}
