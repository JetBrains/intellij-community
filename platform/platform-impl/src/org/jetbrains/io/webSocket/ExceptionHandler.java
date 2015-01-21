package org.jetbrains.io.webSocket;

public interface ExceptionHandler {
  /**
   * @param e Exception while encode message (on send)
   */
  void exceptionCaught(Throwable e);
}
