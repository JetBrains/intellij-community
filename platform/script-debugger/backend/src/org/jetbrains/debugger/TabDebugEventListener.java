package org.jetbrains.debugger;

/**
 * This interface is used by the SDK to report browser-related debug
 * events for a certain tab to the clients.
 */
public interface TabDebugEventListener {
  /**
   * Every {@code TabDebugEventListener} should aggregate
   * {@code DebugEventListener}.
   */
  DebugEventListener getDebugEventListener();

  /**
   * Reports a navigation event on the target tab.
   *
   * @param newUrl the new URL of the debugged tab
   */
  void navigated(String newUrl);
}