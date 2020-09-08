package com.intellij.xdebugger.frame;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

public interface XValueCallback {
  /**
   * Indicate that an error occurs
   * @param errorMessage message describing the error
   */
  void errorOccurred(@NotNull @NlsContexts.DialogMessage String errorMessage);
}