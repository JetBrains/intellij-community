package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;

public interface StringValue extends Value {
  boolean isTruncated();

  int getActualLength();

  /**
   * Asynchronously reloads object value with extended size limit
   */
  ActionCallback reloadHeavyValue();
}