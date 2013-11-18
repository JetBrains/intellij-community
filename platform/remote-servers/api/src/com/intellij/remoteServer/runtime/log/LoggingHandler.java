package com.intellij.remoteServer.runtime.log;

import com.intellij.execution.process.ProcessHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public interface LoggingHandler {
  void print(@NotNull String s);
  void printHyperlink(@NotNull String url);

  void attachToProcess(@NotNull ProcessHandler handler);
}
