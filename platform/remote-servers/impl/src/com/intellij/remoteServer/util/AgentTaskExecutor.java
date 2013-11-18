package com.intellij.remoteServer.util;

import com.intellij.openapi.util.Computable;
import com.intellij.remoteServer.agent.util.CloudAgentErrorHandler;

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
    T result = task.compute();
    if (myErrorMessage == null) {
      return result;
    }
    else {
      throw new ServerRuntimeException(myErrorMessage);
    }
  }

  public <T> void execute(Computable<T> task, CallbackWrapper<T> callback) {
    clear();
    T result = task.compute();
    if (myErrorMessage == null) {
      callback.onSuccess(result);
    }
    else {
      callback.onError(myErrorMessage);
    }
  }
}
