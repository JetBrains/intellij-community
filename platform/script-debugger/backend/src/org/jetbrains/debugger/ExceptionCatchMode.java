package org.jetbrains.debugger;

/**
 * Defines when VM will break on exception throw (before stack unwind happened)
 */
public enum ExceptionCatchMode {
  /**
   * VM always breaks when exception is being thrown
   */
  ALL,

  /**
   * VM breaks when exception is being thrown without try-catch that is going to catch it
   */
  UNCAUGHT,

  /**
   * VM doesn't break when exception is being thrown
   */
  NONE
}