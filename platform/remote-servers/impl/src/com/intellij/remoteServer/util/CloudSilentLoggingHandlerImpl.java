package com.intellij.remoteServer.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.remoteServer.agent.util.CloudLoggingHandler;
import com.intellij.remoteServer.agent.util.log.LogListener;

/**
 * @author michael.golubev
 */
public class CloudSilentLoggingHandlerImpl implements CloudLoggingHandler {

  private static final Logger LOG = Logger.getInstance("#" + CloudSilentLoggingHandlerImpl.class.getName());

  @Override
  public void println(String message) {
    LOG.info(message);
  }

  @Override
  public LogListener getOrCreateLogListener(String pipeName) {
    return LogListener.NULL;
  }
}
