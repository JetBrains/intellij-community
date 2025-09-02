// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IntelliJLogger")
@file:ApiStatus.Experimental

package com.intellij.platform.syntax.psi

import com.intellij.openapi.diagnostic.Logger
import kotlin.jvm.JvmName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun Logger.asSyntaxLogger(): com.intellij.platform.syntax.Logger = IntelliJLoggerAdapter(this)

private class IntelliJLoggerAdapter(private val log: Logger) : com.intellij.platform.syntax.Logger {
  override fun error(string: String) {
    log.error(string)
  }

  override fun isDebugEnabled(): Boolean =
    log.isDebugEnabled

  override fun error(string: String, vararg attachment: com.intellij.platform.syntax.Logger.Attachment) {
    val attachments = attachment
      .map { com.intellij.openapi.diagnostic.Attachment(it.name, it.content) }
      .toTypedArray()
    log.error(string, *attachments)
  }

  override fun warn(string: String, exception: Throwable?) {
    log.warn(string, exception)
  }

  override fun info(string: String, exception: Throwable?) {
    log.info(string, exception)
  }

  override fun debug(string: String, exception: Throwable?) {
    log.debug(string, exception)
  }

  override fun trace(exception: Throwable) {
    log.trace(exception)
  }

  override fun trace(string: String) {
    log.trace(string)
  }
}