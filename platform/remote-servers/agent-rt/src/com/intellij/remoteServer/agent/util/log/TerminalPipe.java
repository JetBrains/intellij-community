/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.agent.util.log;

import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;

import java.io.InputStream;
import java.io.OutputStream;

public abstract class TerminalPipe extends LogPipeBase {

  private final String myLogPipeName;
  private final CloudAgentLoggingHandler myLoggingHandler;
  private TerminalListener myTerminalListener;

  public TerminalPipe(String logPipeName, CloudAgentLoggingHandler loggingHandler) {
    myLogPipeName = logPipeName;
    myLoggingHandler = loggingHandler;
    myTerminalListener = TerminalListener.NULL;
  }

  @Override
  public void open() {
    myTerminalListener = myLoggingHandler.createTerminal(myLogPipeName, getOutputStream(), getInputStream());
  }

  @Override
  public void close() {
    myTerminalListener.close();
  }

  protected abstract OutputStream getOutputStream();

  protected abstract InputStream getInputStream();
}
