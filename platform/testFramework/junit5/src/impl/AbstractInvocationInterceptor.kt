// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.junit.jupiter.api.extension.DynamicTestInvocationContext
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Constructor
import java.lang.reflect.Method

internal abstract class AbstractInvocationInterceptor : InvocationInterceptor {

  abstract fun <T> intercept(invocation: Invocation<T>, context: ExtensionContext): T

  override fun <T : Any?> interceptTestClassConstructor(invocation: Invocation<T>, invocationContext: ReflectiveInvocationContext<Constructor<T>>, extensionContext: ExtensionContext): T? {
    return intercept(invocation, extensionContext)
  }

  override fun interceptBeforeAllMethod(invocation: Invocation<Void?>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
    intercept(invocation, extensionContext)
  }

  override fun interceptBeforeEachMethod(invocation: Invocation<Void?>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
    intercept(invocation, extensionContext)
  }

  override fun interceptTestMethod(invocation: Invocation<Void?>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
    intercept(invocation, extensionContext)
  }

  override fun <T : Any?> interceptTestFactoryMethod(invocation: Invocation<T>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext): T? {
    return intercept(invocation, extensionContext)
  }

  override fun interceptTestTemplateMethod(invocation: Invocation<Void?>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
    intercept(invocation, extensionContext)
  }

  override fun interceptDynamicTest(invocation: Invocation<Void?>, invocationContext: DynamicTestInvocationContext, extensionContext: ExtensionContext) {
    intercept(invocation, extensionContext)
  }

  override fun interceptAfterEachMethod(invocation: Invocation<Void?>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
    intercept(invocation, extensionContext)
  }

  override fun interceptAfterAllMethod(invocation: Invocation<Void?>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
    intercept(invocation, extensionContext)
  }
}
