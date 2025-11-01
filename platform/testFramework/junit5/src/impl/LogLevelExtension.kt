// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testFramework.junit5.LogLevel
import com.intellij.testFramework.junit5.LogLevelWithClass
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.platform.commons.support.AnnotationSupport

@TestOnly
internal class LogLevelExtension : AbstractInvocationInterceptor() {
  override fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, context: ExtensionContext): T {
    val annotations = collectAnnotations<LogLevel>(context).ifEmpty {
      return invocation.proceed()
    }

    val infos = annotations.map {
      LogLevelInfo(Logger.getInstance(it.category), it.level)
    }

    return doWithLogLevelInstalled(infos, invocation)
  }
}

@TestOnly
internal class LogLevelWithClassExtension : AbstractInvocationInterceptor() {
  override fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, context: ExtensionContext): T {
    val annotations = collectAnnotations<LogLevelWithClass>(context).ifEmpty {
      return invocation.proceed()
    }

    val infos = annotations.map {
      LogLevelInfo(Logger.getInstance(it.category.java), it.level)
    }

    return doWithLogLevelInstalled(infos, invocation)
  }
}

private inline fun <reified C : Annotation> collectAnnotations(context: ExtensionContext): List<C> {
  val annotations =
    AnnotationSupport.findRepeatableAnnotations(context.testClass, C::class.java) +
    AnnotationSupport.findRepeatableAnnotations(context.element, C::class.java)
  return annotations.distinct()
}

private data class LogLevelInfo(
  val logger: Logger,
  val level: com.intellij.openapi.diagnostic.LogLevel,
)

private fun <T> doWithLogLevelInstalled(
  infos: List<LogLevelInfo>,
  invocation: InvocationInterceptor.Invocation<T>,
): T {
  val valuesBefore = infos.map { info ->
    setLogLevel(info)
  }
  try {
    return invocation.proceed()
  }
  finally {
    for (handle in valuesBefore.asReversed()) {
      handle()
    }
  }
}

private fun setLogLevel(info: LogLevelInfo): () -> Unit {
  val (logger, logLevel) = info

  val prevLevel = logger.level
  logger.level = logLevel
  return {
    logger.level = prevLevel
  }
}
