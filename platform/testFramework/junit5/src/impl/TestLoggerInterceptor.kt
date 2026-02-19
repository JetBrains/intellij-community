// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor

internal class TestLoggerInterceptor : AbstractInvocationInterceptor() {

  override fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, context: ExtensionContext): T {
    return recordErrorsLoggedInTheCurrentThreadAndReportThemAsFailures {
      invocation.proceed()
    }
  }
}
