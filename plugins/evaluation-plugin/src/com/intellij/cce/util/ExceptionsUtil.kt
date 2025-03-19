// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

import java.io.PrintWriter
import java.io.StringWriter

object ExceptionsUtil {
  fun stackTraceToString(e: Throwable): String {
    val sw = StringWriter()
    e.printStackTrace(PrintWriter(sw))
    return sw.toString()
  }
}