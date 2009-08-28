package com.intellij.openapi.vcs.checkin;

/**
 * Implemented by checkin handlers that need to control the process of running other
 * checkin handlers.
 *
 * @author yole
 */
public interface CheckinMetaHandler {
  void runCheckinHandlers(Runnable runnable);
}