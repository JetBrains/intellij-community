package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.process.ProcessHandler;
import org.jetbrains.annotations.NotNull;

public interface AttachToProcessListener {
  void onAttachToProcess(@NotNull ProcessHandler processHandler);
}
