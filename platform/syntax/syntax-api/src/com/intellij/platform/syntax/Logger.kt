// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface Logger {
  fun error(string: String)

  fun error(string: String, vararg attachment: Attachment)

  fun warn(string: String, exception: Throwable? = null)

  fun info(string: String, exception: Throwable? = null)

  fun debug(string: String, exception: Throwable? = null)

  fun trace(exception: Throwable)

  fun trace(string: String)

  fun isDebugEnabled(): Boolean

  class Attachment(val name: String, val content: String)
}
