package com.intellij.driver.model.transport;

import com.intellij.driver.model.LockSemantics;
import com.intellij.driver.model.OnDispatcher;
import com.intellij.driver.model.RdTarget;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public abstract class RemoteCall implements Serializable {
  @Serial
  private static final long serialVersionUID = 1L;

  private final RdTarget rdTarget;

  private final int sessionId;
  private final String pluginId;
  private final String timedSpan;

  private final OnDispatcher dispatcher;
  private final LockSemantics lockSemantics;
  private final String className;
  private final String methodName;

  public RemoteCall(int sessionId,
                    String timedSpan,
                    String pluginId,
                    OnDispatcher dispatcher,
                    LockSemantics lockSemantics,
                    String className,
                    String methodName,
                    RdTarget rdTarget,
                    Object[] args) {
    this.sessionId = sessionId;
    this.pluginId = pluginId;
    this.timedSpan = timedSpan;
    this.dispatcher = dispatcher;
    this.lockSemantics = lockSemantics;
    this.className = className;
    this.methodName = methodName;
    this.rdTarget = rdTarget;
    this.args = args;
  }

  private final Object[] args;

  public @NotNull RdTarget getRdTarget() {
    return rdTarget;
  }

  public int getSessionId() {
    return sessionId;
  }

  public OnDispatcher getDispatcher() {
    return dispatcher;
  }

  public LockSemantics getLockSemantics() {
    return lockSemantics;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  public Object[] getArgs() {
    return args;
  }

  public String getPluginId() {
    return pluginId;
  }

  public String getTimedSpan() {
    return timedSpan;
  }

  public static boolean isPassByValue(Class<?> clazz) {
    return String.class.isAssignableFrom(clazz)
           || Boolean.class.isAssignableFrom(clazz)
           || boolean.class.isAssignableFrom(clazz)
           || Integer.class.isAssignableFrom(clazz)
           || int.class.isAssignableFrom(clazz)
           || Long.class.isAssignableFrom(clazz)
           || long.class.isAssignableFrom(clazz)
           || Byte.class.isAssignableFrom(clazz)
           || byte.class.isAssignableFrom(clazz)
           || Short.class.isAssignableFrom(clazz)
           || short.class.isAssignableFrom(clazz)
           || Double.class.isAssignableFrom(clazz)
           || double.class.isAssignableFrom(clazz)
           || Float.class.isAssignableFrom(clazz)
           || float.class.isAssignableFrom(clazz)
           || LocalDate.class.isAssignableFrom(clazz)
           || LocalDateTime.class.isAssignableFrom(clazz)
           || Duration.class.isAssignableFrom(clazz)
           || Point.class.isAssignableFrom(clazz)
           || Rectangle.class.isAssignableFrom(clazz)
           || PassByValue.class.isAssignableFrom(clazz);
  }

  @Contract("null -> true")
  public static boolean isPassByValue(Object result) {
    // does not need a session, pass by value
    return result == null
           || result instanceof String
           || result instanceof Boolean
           || result instanceof Integer
           || result instanceof Long
           || result instanceof Byte
           || result instanceof Short
           || result instanceof Double
           || result instanceof Float
           || result instanceof LocalDate
           || result instanceof LocalDateTime
           || result instanceof Duration
           || result instanceof Point
           || result instanceof Rectangle
           || result instanceof PassByValue;
  }
}