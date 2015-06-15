package com.intellij.remoteServer.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;
import com.intellij.remoteServer.agent.util.log.LogListener;
import com.intellij.remoteServer.agent.util.log.TerminalListener;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author michael.golubev
 */
public class CloudSilentLoggingHandlerImpl implements CloudAgentLoggingHandler {

  private static final Logger LOG = Logger.getInstance("#" + CloudSilentLoggingHandlerImpl.class.getName());

  @Override
  public void println(String message) {
    LOG.info(message);
  }

  @Override
  public LogListener getOrCreateLogListener(String pipeName) {
    return LogListener.NULL;
  }

  @Override
  public LogListener getOrCreateEmptyLogListener(String pipeName) {
    return LogListener.NULL;
  }

  @Override
  public LogListener createConsole(String pipeName, OutputStream consoleInput) {
    return LogListener.NULL;
  }

  @Override
  public boolean isTtySupported() {
    return false;
  }

  @Override
  public TerminalListener createTerminal(String pipeName, OutputStream terminalInput, InputStream terminalOutput) {
    return TerminalListener.NULL;
  }
}
