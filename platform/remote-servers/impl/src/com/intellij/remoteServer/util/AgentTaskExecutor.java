package com.intellij.remoteServer.util;

import com.intellij.openapi.util.Computable;
import com.intellij.remoteServer.agent.util.CloudAgentErrorHandler;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

/**
 * @author michael.golubev
 */
public class AgentTaskExecutor implements CloudAgentErrorHandler {

  private String myErrorMessage;

  @Override
  public void onError(String message) {
    myErrorMessage = message;
  }

  private void clear() {
    myErrorMessage = null;
  }

  public <T> T execute(Computable<T> task) throws ServerRuntimeException {
    clear();
    T result;
    try {
      result = task.compute();
    }
    catch (CancellationException e) {
      throw new ServerRuntimeException(safeMessage(e));
    }
    if (myErrorMessage == null) {
      return result;
    }
    else {
      throw new ServerRuntimeException(myErrorMessage);
    }
  }

  public <T> void execute(Computable<T> task, CallbackWrapper<T> callback) {
    clear();
    T result;
    try {
      result = task.compute();
    }
    catch (CancellationException e) {
      onError(safeMessage(e));
      result = null;
    }

    if (myErrorMessage == null) {
      callback.onSuccess(result);
    }
    else {
      callback.onError(myErrorMessage);
    }
  }

  private static String safeMessage(@NotNull CancellationException ex) {
    return ObjectUtils.notNull(ex.getMessage(), "Operation cancelled");
  }
}
