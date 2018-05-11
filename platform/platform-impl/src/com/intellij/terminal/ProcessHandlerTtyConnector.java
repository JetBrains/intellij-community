/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.terminal;

import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.UnixProcessManager;
import com.intellij.openapi.util.SystemInfo;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * @author traff
 */
public class ProcessHandlerTtyConnector implements TtyConnector {
  private static final int SIGWINCH = 28;

  private final OSProcessHandler myProcessHandler;
  private final PtyProcess myPtyProcess;
  protected Charset myCharset;

  public ProcessHandlerTtyConnector(@NotNull ProcessHandler processHandler, @NotNull Charset charset) {
    if (!(processHandler instanceof OSProcessHandler)) {
      throw new IllegalArgumentException("Works currently only with OSProcessHandler");
    }
    else {
      myProcessHandler = (OSProcessHandler)processHandler;
    }
    if (!(myProcessHandler.getProcess() instanceof PtyProcess)) {
      throw new IllegalArgumentException("Should be a PTY based process");
    }
    else {
      myPtyProcess = (PtyProcess)myProcessHandler.getProcess();
    }
    myCharset = charset;
  }

  @Override
  public boolean init(Questioner q) {
    return true;
  }

  @Override
  public void close() {
    myProcessHandler.destroyProcess();
  }

  @Override
  public void resize(Dimension termSize, Dimension pixelSize) {
    if (termSize != null && pixelSize != null) {
      if (myPtyProcess.isRunning()) {
        myPtyProcess.setWinSize(
          new WinSize(termSize.width, termSize.height, pixelSize.width, pixelSize.height));
        if (SystemInfo.isUnix) {
          UnixProcessManager.sendSignalToProcessTree(myPtyProcess.getPid(), SIGWINCH);
        }
      }
    }
  }

  @Override
  public String getName() {
    return "TtyConnector:" + myProcessHandler.toString();
  }

  @Override
  public int read(char[] buf, int offset, int length) throws IOException {
    throw new IllegalStateException("all reads should be performed by ProcessHandler");
  }

  @Override
  public void write(byte[] bytes) throws IOException {
    myProcessHandler.getProcessInput().write(bytes);
    myProcessHandler.getProcessInput().flush();
  }

  @Override
  public boolean isConnected() {
    return false;
  }

  @Override
  public void write(String string) throws IOException {
    myProcessHandler.getProcessInput().write(string.getBytes(myCharset));
    myProcessHandler.getProcessInput().flush();
  }

  @Override
  public int waitFor() throws InterruptedException {
    return myPtyProcess.waitFor();
  }
}
