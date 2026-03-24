// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.events.test;

import org.gradle.tooling.events.OperationDescriptor;
import org.gradle.tooling.events.test.JvmTestKind;
import org.gradle.tooling.events.test.JvmTestOperationDescriptor;
import org.gradle.tooling.events.test.source.TestSource;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class InternalJvmTestOperationDescriptor extends InternalTestOperationDescriptor implements JvmTestOperationDescriptor {

  private final JvmTestKind jvmTestKind;
  private final String suiteName;
  private final String className;
  private final String methodName;
  private final TestSource testSource;

  public InternalJvmTestOperationDescriptor(Object id,
                                            String name,
                                            String displayName,
                                            OperationDescriptor parent,
                                            JvmTestKind jvmTestKind,
                                            String suiteName,
                                            String className,
                                            String methodName,
                                            TestSource testSource) {
    super(id, name, displayName, parent);
    this.jvmTestKind = jvmTestKind;
    this.suiteName = suiteName;
    this.className = className;
    this.methodName = methodName;
    this.testSource = testSource;
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

  @Override
  public TestSource getSource() {
    return testSource;
  }
}
