// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.rt.junit.JUnitStarter;
import org.junit.jupiter.api.extension.DynamicTestInvocationContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

import java.lang.reflect.Method;

/**
 * Runs no test body while {@link JUnitStarter#DRY_RUN_PROPERTY} is set, so the runner reports the whole test tree and changes nothing.
 * The IDE reads the parameters of a data-driven test out of that tree.
 * <p>
 * Only a test body is skipped. The constructor of the test class, the lifecycle methods and the body of a {@code @TestFactory} still
 * run: the factory has to produce its dynamic tests, and a parameter provider may need what a lifecycle method set up.
 */
public final class DryRunInterceptor implements InvocationInterceptor {
  @Override
  public void interceptTestMethod(Invocation<Void> invocation,
                                  ReflectiveInvocationContext<Method> invocationContext,
                                  ExtensionContext extensionContext) throws Throwable {
    skipDuringDryRun(invocation, extensionContext);
  }

  @Override
  public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
    skipDuringDryRun(invocation, extensionContext);
  }

  @Override
  public void interceptDynamicTest(Invocation<Void> invocation,
                                   DynamicTestInvocationContext invocationContext,
                                   ExtensionContext extensionContext) throws Throwable {
    skipDuringDryRun(invocation, extensionContext);
  }

  /** Only an {@code Invocation<Void>} may be skipped: {@code skip()} returns null, and a caller of it needs no value. */
  private static void skipDuringDryRun(Invocation<Void> invocation, ExtensionContext extensionContext) throws Throwable {
    boolean dryRun = extensionContext.getConfigurationParameter(JUnitStarter.DRY_RUN_PROPERTY)
      .map(Boolean::parseBoolean).orElse(false);
    if (dryRun) {
      invocation.skip();
    }
    else {
      invocation.proceed();
    }
  }
}
