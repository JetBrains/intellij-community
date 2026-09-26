package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.test.source.MethodSource;
import org.jetbrains.annotations.ApiStatus;

import java.io.Serializable;

@ApiStatus.Internal
public class InternalDefaultMethodSource implements Serializable, MethodSource {

  private final String className;
  private final String methodName;

  public InternalDefaultMethodSource(String className, String methodName) {
    this.className = className;
    this.methodName = methodName;
  }

  @Override
  public String getClassName() {
    return className;
  }

  @Override
  public String getMethodName() {
    return methodName;
  }
}
