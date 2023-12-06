// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.nullize
import com.intellij.util.lang.CompoundRuntimeException
import org.jetbrains.annotations.ApiStatus

fun throwIfNotEmpty(errors: List<Throwable?>?) {
  val exceptions = errors?.filterNotNull().nullize() ?: return
  val throwable = CompoundRuntimeException(exceptions)

  val result = ArrayList<Throwable>()
  val queue = ArrayDeque<Throwable>()
  queue.add(throwable)
  var iterations = 0
  while (queue.isNotEmpty() && (iterations++ < 100)) {
    when (val exception = queue.removeFirst()) {
      is CompoundRuntimeException -> queue.addAll(exception.exceptions)
      else -> result.add(exception)
    }
  }
  if (queue.isNotEmpty()) {
    logger<CompoundRuntimeException>().warn("Exception is too complex")
    throw throwable
  }
  throw result.singleOrNull() ?: CompoundRuntimeException(result)
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