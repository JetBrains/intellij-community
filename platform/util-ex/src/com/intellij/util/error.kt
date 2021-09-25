// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.util.lang.CompoundRuntimeException
import org.jetbrains.annotations.ApiStatus

fun throwIfNotEmpty(errors: List<Throwable>) {
  val size = errors.size
  if (size == 1) {
    throw errors.first()
  }
  else if (size != 0) {
    throw CompoundRuntimeException(errors)
  }
}

@ApiStatus.Internal
fun getErrorsAsString(errors: List<Throwable>, includeStackTrace: Boolean = true): CharSequence {
  val sb = StringBuilder()
  sb.append("${errors.size} errors:\n")
  for (i in errors.indices) {
    sb.append("[").append(i + 1).append("]: ------------------------------\n")
    val error = errors[i]
    val line = if (includeStackTrace) ExceptionUtil.getThrowableText(error) else error.message!!
    sb.append(line)
    if (!line.endsWith('\n')) {
      sb.append('\n')
    }
  }
  sb.append("-".repeat(5))
  sb.append("------------------------------\n")
  return sb
}