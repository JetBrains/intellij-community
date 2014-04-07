package org.jetbrains.debugger.values;

import com.intellij.openapi.util.ActionCallback;

public interface StringValue extends Value {
  boolean isTruncated();

  int getLength();

  /**
   * Asynchronously reloads object value with extended size limit
   */
  ActionCallback reloadHeavyValue();
}