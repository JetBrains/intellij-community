// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface Logger {
  fun error(string: String)
  fun error(string: String, vararg attachment: Attachment)
  fun warn(string: String, exception: RuntimeException? = null)

  class Attachment(val name: String?, val content: String)
}

internal object NoopLogger : Logger {
  override fun error(string: String) {}
  override fun error(string: String, vararg attachment: Logger.Attachment) {}
  override fun warn(string: String, exception: RuntimeException?) {}
}