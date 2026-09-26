// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import org.junit.jupiter.api.extension.DynamicTestInvocationContext
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method

interface LoggedErrorProcessorEnabler : InvocationInterceptor {
  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    runTest { invocation.proceed() }
  }

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    runTest { invocation.proceed() }
  }


  override fun interceptDynamicTest(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: DynamicTestInvocationContext,
    extensionContext: ExtensionContext,
  ) {
    runTest { invocation.proceed() }
  }

  private fun runTest(test: () -> Unit) {
    val processor = createErrorProcessor()
    LoggedErrorProcessor.executeWith<Throwable>(processor) {
      test()
    }
  }

  fun createErrorProcessor(): LoggedErrorProcessor

  class DoNoRethrowErrors : LoggedErrorProcessorEnabler {
    override fun createErrorProcessor(): LoggedErrorProcessor {
      return object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
          return setOf(Action.LOG)
        }
      }
    }
  }
}
