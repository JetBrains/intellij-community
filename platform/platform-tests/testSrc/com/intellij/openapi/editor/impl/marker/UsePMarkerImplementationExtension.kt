// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.marker

import com.intellij.openapi.editor.impl.RangeMarkersImpl
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method

@TestOnly
class UsePMarkerImplementationExtension : InvocationInterceptor {
  override fun interceptBeforeAllMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, extensionContext)
  }

  override fun interceptBeforeEachMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, extensionContext)
  }

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, extensionContext)
  }

  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, extensionContext)
  }

  override fun interceptAfterEachMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, extensionContext)
  }

  override fun interceptAfterAllMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, extensionContext)
  }

  private fun intercept(
    invocation: InvocationInterceptor.Invocation<Void?>,
    extensionContext: ExtensionContext,
  ) {
    val annotation = extensionContext.testMethod.orElse(null)?.getAnnotation(UsePMarkerImplementation::class.java)
                     ?: extensionContext.requiredTestClass.getAnnotation(UsePMarkerImplementation::class.java)
                     ?: error("The range marker implementation annotation is missing")
    RangeMarkersImpl.usePMarkerImplementationIn<RuntimeException>(annotation.usePMarkerImplementation) {
      invocation.proceed()
    }
  }
}

/** Selects the range marker implementation for a JUnit 5 test and its setup and teardown methods. */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@ExtendWith(
  UsePMarkerImplementationExtension::class
)
annotation class UsePMarkerImplementation(val usePMarkerImplementation: Boolean = true)
