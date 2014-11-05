package org.jetbrains.debugger.values;

import org.jetbrains.concurrency.Promise;

public interface StringValue extends Value {
  boolean isTruncated();

  int getLength();

  /**
   * Asynchronously reloads object value with extended size limit
   */
  Promise<String> getFullString();
}