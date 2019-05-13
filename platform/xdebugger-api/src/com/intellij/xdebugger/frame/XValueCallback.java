package com.intellij.xdebugger.frame;

import org.jetbrains.annotations.NotNull;

public interface XValueCallback {
  /**
   * Indicate that an error occurs
   * @param errorMessage message describing the error
   */
  void errorOccurred(@NotNull String errorMessage);
}