// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.junit5;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.RunsInEdt;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.reflect.Method;

public class EdtInterceptor implements InvocationInterceptor {
  private final boolean myAnnotationDependant;

  public EdtInterceptor() {
    this(false);
  }

  public EdtInterceptor(boolean annotationDependant) {
    myAnnotationDependant = annotationDependant;
  }

  private void doRun(Invocation<Void> invocation, ExtensionContext extensionContext) throws Throwable {
    if (myAnnotationDependant && 
        AnnotationSupport.findAnnotation(extensionContext.getTestClass(), RunsInEdt.class).isEmpty() &&
        AnnotationSupport.findAnnotation(extensionContext.getTestMethod(), RunsInEdt.class).isEmpty()) {
      invocation.proceed();
      return;
    }
    EdtTestUtil.runInEdtAndWait(() -> invocation.proceed());
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
}