package com.intellij.driver.model.transport;

import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;
import com.intellij.driver.model.RdTarget;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

public final class ServiceCall extends RemoteCall {
  @Serial
  private static final long serialVersionUID = 1L;

  private final Ref projectRef;
  private final String serviceInterface;

  public ServiceCall(int sessionId,
                     String timedSpan,
                     String pluginId,
                     OnDispatcher dispatcher,
                     LockSemantics lockSemantics,
                     String className,
                     String methodName,
                     Object[] args,
                     @Nullable Ref projectRef,
                     @Nullable String serviceInterface,
                     RdTarget rdTarget) {
    super(sessionId, timedSpan, pluginId, dispatcher, lockSemantics, className, methodName, rdTarget, args);
    this.projectRef = projectRef;
    this.serviceInterface = serviceInterface;
  }

  public Ref getProjectRef() {
    return projectRef;
  }

  public @Nullable String getServiceInterface() {
    return serviceInterface;
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
