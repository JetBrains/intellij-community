package com.intellij.remote;

import com.intellij.execution.TaskExecutor;
import com.intellij.execution.process.ProcessHandler;

/**
 * @author Alexander Koshevoy
 */
public abstract class AbstractRemoteProcessHandler<T extends RemoteProcess> extends ProcessHandler implements TaskExecutor {
  public abstract T getProcess();
}
