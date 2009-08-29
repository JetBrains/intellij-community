package com.intellij.xdebugger;

/**
 * @author yole
 */
public interface AbstractDebuggerSession {
  boolean isStopped();
  boolean isPaused();
}
