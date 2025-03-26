// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.containers.nullize
import com.intellij.util.lang.CompoundRuntimeException

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