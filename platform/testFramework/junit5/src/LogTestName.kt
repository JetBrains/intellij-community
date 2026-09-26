// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5

import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.orNull
import org.junit.jupiter.api.extension.DynamicTestInvocationContext
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Method

class LogTestName : InvocationInterceptor {
  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    runTest(extensionContext, invocation)
  }

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    runTest(extensionContext, invocation)
  }


  override fun interceptDynamicTest(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: DynamicTestInvocationContext,
    extensionContext: ExtensionContext,
  ) {
    runTest(extensionContext, invocation)
  }

  private fun runTest(extensionContext: ExtensionContext, invocation: InvocationInterceptor.Invocation<Void>) {
    val logger = getLogger(extensionContext)
    val displayName =
      generateSequence(extensionContext) { it.parent.orNull() }
        .map(ExtensionContext::getDisplayName)
        .reduce { child, parent -> "$parent::$child" }
    logger.info("""
        
        =======================
        Started a test: $displayName
        =======================
      """.trimIndent())
    try {
      invocation.proceed()
      logger.info("""
          
          =======================
          The test finished successfully: $displayName
          =======================
        """.trimIndent())
    }
    catch (err: Throwable) {
      logger.info("""
          |
          |=======================
          |The test failed: $displayName
          |${getStackTrace(err)}
          |=======================
        """.trimMargin())
      throw err
    }
  }

  private fun getStackTrace(cause: Throwable): String =
    buildString {
      append(cause.stackTraceToString())
      if (cause is ExceptionWithAttachments) {
        for (attachment in cause.attachments) {
          append("\n*** Attachment: ${attachment.displayText}\n")
          append(attachment.encodedBytes)
        }
      }
    }

  private fun getLogger(extensionContext: ExtensionContext) =
    Logger.getInstance(extensionContext.testClass.orElse(javaClass))
}