package com.intellij.xdebugger;

import com.intellij.execution.process.ProcessHandler;

import java.io.OutputStream;

/**
 * @author nik
 */
public class DefaultDebugProcessHandler extends ProcessHandler {
  protected void destroyProcessImpl() {
    notifyProcessTerminated(0);
  }

  protected void detachProcessImpl() {
    notifyProcessDetached();
  }

  public boolean detachIsDefault() {
    return true;
  }

  public OutputStream getProcessInput() {
    return null;
  }
}
