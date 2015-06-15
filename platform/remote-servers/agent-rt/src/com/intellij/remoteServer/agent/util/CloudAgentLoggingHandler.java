/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.remoteServer.agent.util;

import com.intellij.remoteServer.agent.util.log.LogListener;
import com.intellij.remoteServer.agent.util.log.TerminalListener;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author michael.golubev
 */
public interface CloudAgentLoggingHandler {

  void println(String message);

  LogListener getOrCreateLogListener(String pipeName);

  LogListener getOrCreateEmptyLogListener(String pipeName);

  LogListener createConsole(String pipeName, OutputStream consoleInput);

  boolean isTtySupported();

  TerminalListener createTerminal(String pipeName, OutputStream terminalInput, InputStream terminalOutput);
}
