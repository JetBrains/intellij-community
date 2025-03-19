// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.JvmTestKind;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class InternalJvmTestOperationDescriptor extends InternalTestOperationDescriptor implements JvmTestOperationDescriptor {

  private final JvmTestKind jvmTestKind;
  private final String suiteName;
  private final String className;
  private final String methodName;

  public InternalJvmTestOperationDescriptor(Object id,
                                            String name,
                                            String displayName,
                                            OperationDescriptor parent,
                                            JvmTestKind kind,
                                            String suiteName,
                                            String className,
                                            String methodName) {
    super(id, name, displayName, parent);
    jvmTestKind = kind;
    this.suiteName = suiteName;
    this.className = className;
    this.methodName = methodName;
  }

  @Override
  public JvmTestKind getJvmTestKind() {
    return jvmTestKind;
  }

  @Override
  public String getSuiteName() {
    return suiteName;
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
