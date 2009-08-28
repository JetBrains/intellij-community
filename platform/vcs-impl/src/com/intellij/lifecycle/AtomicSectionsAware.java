package com.intellij.lifecycle;

import com.intellij.openapi.progress.ProcessCanceledException;

public interface AtomicSectionsAware {
  void enter();
  void exit();
  boolean shouldExitAsap();
  void checkShouldExit() throws ProcessCanceledException;
}
