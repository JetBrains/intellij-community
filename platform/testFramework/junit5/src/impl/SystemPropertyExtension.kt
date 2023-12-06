// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.junit5.SystemProperty
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.support.AnnotationSupport
import java.lang.reflect.Constructor
import java.lang.reflect.Method

@TestOnly
internal class SystemPropertyExtension : InvocationInterceptor {

  override fun interceptBeforeAllMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun <T> interceptTestClassConstructor(
    invocation: InvocationInterceptor.Invocation<T>,
    invocationContext: ReflectiveInvocationContext<Constructor<T>>,
    extensionContext: ExtensionContext,
  ): T {
    return intercept(invocation, invocationContext)
  }

  override fun interceptBeforeEachMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptAfterEachMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptAfterAllMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  private fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, invocationContext: ReflectiveInvocationContext<*>): T {
    val annotations = AnnotationSupport.findRepeatableAnnotations(invocationContext.targetClass, SystemProperty::class.java) +
                      AnnotationSupport.findRepeatableAnnotations(invocationContext.executable, SystemProperty::class.java)
    if (annotations.isEmpty()) {
      return invocation.proceed()
    }
    val valuesBefore = annotations.map { annotation ->
      Pair(annotation, annotation.setPropertyValue())
    }
    try {
      return invocation.proceed()
    }
    finally {
      for ((annotation, previousValue) in valuesBefore.asReversed()) {
        annotation.resetPropertyValue(previousValue)
      }
    }
  }

  private fun SystemProperty.setPropertyValue(): String? {
    val previousValue = System.getProperty(propertyKey)
    if (propertyValue.isEmpty()) {
      System.clearProperty(propertyKey)
    }
    else {
      System.setProperty(propertyKey, propertyValue)
    }
    return previousValue
  }

  private fun SystemProperty.resetPropertyValue(previousValue: String?) {
    if (previousValue == null) {
      System.clearProperty(propertyKey)
    }
    else {
      System.setProperty(propertyKey, previousValue)
    }
  }
}
