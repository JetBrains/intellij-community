// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

public class ProcessWrapper extends Process {

  private final @NotNull Process myOriginalProcess;

  public ProcessWrapper(@NotNull Process originalProcess) {
    myOriginalProcess = originalProcess;
  }

  @Override
  public OutputStream getOutputStream() {
    return myOriginalProcess.getOutputStream();
  }

  @Override
  public InputStream getInputStream() {
    return myOriginalProcess.getInputStream();
  }

  @Override
  public InputStream getErrorStream() {
    return myOriginalProcess.getErrorStream();
  }

  @Override
  public int waitFor() throws InterruptedException {
    return myOriginalProcess.waitFor();
  }

  @Override
  public int exitValue() {
    return myOriginalProcess.exitValue();
  }

  @Override
  public void destroy() {
    myOriginalProcess.destroy();
  }

  public @NotNull Process getOriginalProcess() {
    return myOriginalProcess;
  }
}
