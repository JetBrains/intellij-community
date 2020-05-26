// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.intellij.execution.process.BaseProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.jediterm.terminal.Questioner;
import com.jediterm.terminal.TtyConnector;
import com.pty4j.PtyProcess;
import com.pty4j.WinSize;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class ProcessHandlerTtyConnector implements TtyConnector {

  private final BaseProcessHandler<?> myProcessHandler;
  private final Process myPtyProcess;
  private final Charset myCharset;

  public ProcessHandlerTtyConnector(@NotNull ProcessHandler processHandler, @NotNull Charset charset) {
    if (!(processHandler instanceof BaseProcessHandler)) {
      throw new IllegalArgumentException("Works currently only with BaseProcessHandler");
    }
    myProcessHandler = (BaseProcessHandler<?>)processHandler;
    myPtyProcess = myProcessHandler.getProcess();
    if (!(myPtyProcess instanceof PtyBasedProcess) && !(myPtyProcess instanceof PtyProcess)) {
      throw new IllegalArgumentException("Not a PTY based process: " + myPtyProcess.getClass());
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
      if (myPtyProcess instanceof PtyProcess) {
        PtyProcess ptyProcess = (PtyProcess)myPtyProcess;
        if (ptyProcess.isRunning()) {
          ptyProcess.setWinSize(new WinSize(termSize.width, termSize.height, pixelSize.width, pixelSize.height));
        }
      }
      else {
        assert myPtyProcess instanceof PtyBasedProcess;
        ((PtyBasedProcess)myPtyProcess).resizePtyWindow(termSize.width, termSize.height, pixelSize.width, pixelSize.height);
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
    writeBytes(bytes);
  }

  @Override
  public boolean isConnected() {
    return false;
  }

  @Override
  public void write(String string) throws IOException {
    writeBytes(string.getBytes(myCharset));
  }

  @Override
  public int waitFor() throws InterruptedException {
    return myPtyProcess.waitFor();
  }

  private void writeBytes(byte[] bytes) throws IOException {
    OutputStream input = myProcessHandler.getProcessInput();
    input.write(bytes);
    input.flush();
  }
}
