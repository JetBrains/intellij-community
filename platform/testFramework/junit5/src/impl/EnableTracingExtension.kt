// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.junit5.EnableTracingFor
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.platform.commons.support.AnnotationSupport

@TestOnly
internal class EnableTracingExtension : AbstractInvocationInterceptor() {
  override fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, context: ExtensionContext): T {
    val annotations = collectAnnotations<EnableTracingFor>(context).ifEmpty {
      return invocation.proceed()
    }

    val loggers = annotations.flatMap { annotation ->
      annotation.categories.map { Logger.getInstance(it) } +
      annotation.categoryClasses.map { Logger.getInstance(it.java) }
    }

    return doWithTracingEnabled(loggers, invocation)
  }

  private fun <T> doWithTracingEnabled(
    infos: List<Logger>,
    invocation: InvocationInterceptor.Invocation<T>,
  ): T {
    val valuesBefore = infos.map { info -> enableTracing(info) }

    try {
      return invocation.proceed()
    }
    finally {
      valuesBefore.asReversed().forEach { it() }
    }
  }

  private fun enableTracing(logger: Logger): () -> Unit {
    logger.setLevel(LogLevel.TRACE)
    return {
      logger.setLevel(LogLevel.INFO)
    }
  }

  private inline fun <reified C : Annotation> collectAnnotations(context: ExtensionContext): List<C> {
    val annotations =
      AnnotationSupport.findRepeatableAnnotations(context.testClass, C::class.java) +
      AnnotationSupport.findRepeatableAnnotations(context.element, C::class.java)
    return annotations.distinct()
  }
}