// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author Konstantin Kolosovsky.
 */
public class ProcessWrapper extends Process {

  @NotNull private final Process myOriginalProcess;

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

  @NotNull
  public Process getOriginalProcess() {
    return myOriginalProcess;
  }
}
