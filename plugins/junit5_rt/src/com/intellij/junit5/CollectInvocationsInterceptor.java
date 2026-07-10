// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.Method;

public final class CollectInvocationsInterceptor implements InvocationInterceptor {
  public static final String COLLECT_PARAMETERS_PROPERTY = "idea.junit.collect.parameters";

  @Override
  public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
    process(invocation, extensionContext);
  }

  @Override
  public void interceptDynamicTest(Invocation<Void> invocation,
                                   DynamicTestInvocationContext invocationContext,
                                   ExtensionContext extensionContext) throws Throwable {
    process(invocation, extensionContext);
  }

  private static void process(Invocation<Void> invocation, ExtensionContext extensionContext) throws Throwable {
    boolean collecting = extensionContext.getConfigurationParameter(COLLECT_PARAMETERS_PROPERTY)
      .map(Boolean::parseBoolean).orElse(false);
    if (collecting) {
      // fake run. read all tests e.g. dynamic test
      invocation.skip();
    }
    else {
      // normal run
      invocation.proceed();
    }
  }
}