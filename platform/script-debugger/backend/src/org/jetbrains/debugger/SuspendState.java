package org.jetbrains.debugger;

public enum SuspendState {
  /**
   * A normal suspension (a step end or a breakpoint)
   */
  NORMAL,

  /**
   * A suspension due to an exception
   */
  EXCEPTION,

  /**
   * A suspension due to pause command
   */
  PAUSED,
}