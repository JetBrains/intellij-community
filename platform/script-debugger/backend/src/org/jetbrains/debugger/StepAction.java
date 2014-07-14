package org.jetbrains.debugger;

/**
 * JavaScript debugger step actions.
 */
public enum StepAction {
  /**
   * Resume the JavaScript execution.
   */
  CONTINUE,

  /**
   * Step into the current statement.
   */
  IN,

  /**
   * Step over the current statement.
   */
  OVER,

  /**
   * Step out of the current function.
   */
  OUT
}