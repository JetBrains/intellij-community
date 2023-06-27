package com.intellij.driver.model.transport;

import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

public final class ServiceCall extends RemoteCall {
  @Serial
  private static final long serialVersionUID = 1L;

  private final Ref projectRef;

  public ServiceCall(int sessionId,
                     String timedSpan,
                     String pluginId,
                     OnDispatcher dispatcher,
                     LockSemantics lockSemantics,
                     String className,
                     String methodName,
                     Object[] args,
                     @Nullable Ref projectRef) {
    super(sessionId, timedSpan, pluginId, dispatcher, lockSemantics, className, methodName, args);
    this.projectRef = projectRef;
  }

  public Ref getProjectRef() {
    return projectRef;
  }

  @Override
  public String toString() {
    return "ServiceCall{" +
           "className=" + getClassName() +
           " methodName=" + getMethodName() +
           " projectRef=" + projectRef +
           '}';
  }
}
