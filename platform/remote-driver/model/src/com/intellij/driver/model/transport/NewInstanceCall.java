package com.intellij.driver.model.transport;

import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;

import java.io.Serial;

public final class NewInstanceCall extends RemoteCall {
  @Serial
  private static final long serialVersionUID = 1L;

  public NewInstanceCall(int sessionId,
                         String timedSpan,
                         String pluginId,
                         OnDispatcher dispatcher,
                         LockSemantics lockSemantics,
                         String className,
                         Object[] args) {
    super(sessionId, timedSpan, pluginId, dispatcher, lockSemantics, className, "new", args);
  }

  @Override
  public String toString() {
    return "NewInstanceCall{" +
           "className=" + getClassName() +
           '}';
  }
}
