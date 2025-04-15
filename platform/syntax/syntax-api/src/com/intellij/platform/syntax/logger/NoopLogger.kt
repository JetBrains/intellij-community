// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental
@file:JvmName("NoopLogger")

package com.intellij.platform.syntax.logger

import com.intellij.platform.syntax.Logger
import kotlin.jvm.JvmName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
fun noopLogger(): Logger = _NoopLogger

private object _NoopLogger : Logger {
  override fun error(string: String) {
  }

  override fun error(string: String, vararg attachment: Logger.Attachment) {
  }

  override fun warn(string: String, exception: Throwable?) {
  }

  override fun info(string: String, exception: Throwable?) {
  }

  override fun debug(string: String, exception: Throwable?) {
  }

  override fun trace(exception: Throwable) {
  }

  override fun trace(string: String) {
  }

  override fun isDebugEnabled(): Boolean = false
}