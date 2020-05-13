// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;
import com.intellij.remoteServer.agent.util.log.LogListener;
import com.intellij.remoteServer.agent.util.log.TerminalListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;

public class CloudSilentLoggingHandlerImpl implements CloudAgentLoggingHandler {

  private static final Logger LOG = Logger.getInstance(CloudSilentLoggingHandlerImpl.class);

  private final String myProjectLocationHash;

  /**
   * @deprecated left for compatibility, remove for 2020.1
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  @Deprecated
  public CloudSilentLoggingHandlerImpl() {
    this(null);
  }

  public CloudSilentLoggingHandlerImpl(@Nullable Project project) {
    myProjectLocationHash = project == null ? "" : project.getLocationHash();
  }

  @Override
  public String getProjectHash() {
    return myProjectLocationHash;
  }

  @Override
  public void println(String message) {
    LOG.info(message);
  }

  @Override
  public LogListener getOrCreateLogListener(String pipeName) {
    return LogListener.NULL;
  }

  @Override
  public boolean isTtySupported() {
    return false;
  }

  @Override
  public TerminalListener createTerminal(String pipeName, OutputStream terminalInput, InputStream terminalOutput, InputStream stderr) {
    return TerminalListener.NULL;
  }
}
