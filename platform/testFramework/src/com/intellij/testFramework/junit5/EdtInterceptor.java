// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.RunsInEdt;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @deprecated use {@link com.intellij.testFramework.junit5.RunInEdt} in JUnit 5
 */
@Deprecated(forRemoval = true)
public class EdtInterceptor implements InvocationInterceptor {
  private final boolean myAnnotationDependant;

  public EdtInterceptor() {
    this(false);
  }

  public EdtInterceptor(boolean annotationDependant) {
    myAnnotationDependant = annotationDependant;
  }

  private <T> T doRun(Invocation<T> invocation, ExtensionContext extensionContext) throws Throwable {
    if (myAnnotationDependant && 
        AnnotationSupport.findAnnotation(extensionContext.getTestClass(), RunsInEdt.class).isEmpty() &&
        AnnotationSupport.findAnnotation(extensionContext.getTestMethod(), RunsInEdt.class).isEmpty()) {
      return invocation.proceed();
    }
    AtomicReference<T> result = new AtomicReference<>();
    EdtTestUtil.runInEdtAndWait(() -> result.set(invocation.proceed()));
    return result.get();
  }

  @Override
  public void interceptTestTemplateMethod(Invocation<Void> invocation,
                                          ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
    doRun(invocation, extensionContext);
  }

  @Override
  public void interceptTestMethod(Invocation<Void> invocation,
                                  ReflectiveInvocationContext<Method> invocationContext,
                                  ExtensionContext extensionContext) throws Throwable {
    doRun(invocation, extensionContext);
  }

  @Override
  public void interceptBeforeEachMethod(Invocation<Void> invocation,
                                        ReflectiveInvocationContext<Method> invocationContext,
                                        ExtensionContext extensionContext) throws Throwable {
    doRun(invocation, extensionContext);
  }

  @Override
  public void interceptAfterEachMethod(Invocation<Void> invocation,
                                       ReflectiveInvocationContext<Method> invocationContext,
                                       ExtensionContext extensionContext) throws Throwable {
    doRun(invocation, extensionContext);
  }

  @Override
  public <T> T interceptTestFactoryMethod(Invocation<T> invocation,
                                          ReflectiveInvocationContext<Method> invocationContext,
                                          ExtensionContext extensionContext) throws Throwable {
    return doRun(invocation, extensionContext);
  }
}