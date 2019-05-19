// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;

/**
 * @author Roman Chernyatchik
 */
public interface ProcessOutputConsumer extends Disposable {
  void setProcessor(GeneralTestEventsProcessor processor);
  void process(String text, Key outputType);

  /**
   * @deprecated use {@link #flushBufferOnProcessTermination(int)}
   */
  @Deprecated
  default void flushBufferBeforeTerminating() {}

  default void flushBufferOnProcessTermination(int exitCode) {
    flushBufferBeforeTerminating();
  }
}