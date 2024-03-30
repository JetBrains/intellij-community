package com.intellij.driver.model.transport;

import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;
import com.intellij.driver.model.RdTarget;

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
                         RdTarget rdTarget,
                         Object[] args) {
    super(sessionId, timedSpan, pluginId, dispatcher, lockSemantics, className, "new", rdTarget, args);
    if (rdTarget != RdTarget.FRONTEND_ONLY && rdTarget != RdTarget.BACKEND_ONLY) {
      throw new IllegalArgumentException("NewInstanceCall must have RdTarget set to either FRONTEND_ONLY or BACKEND_ONLY");
    }
  }

  @Override
  public String toString() {
    return "NewInstanceCall{" +
           "className=" + getClassName() +
           '}';
  }
}
