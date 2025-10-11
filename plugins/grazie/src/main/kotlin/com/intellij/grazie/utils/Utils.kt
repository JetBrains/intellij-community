// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import ai.grazie.gec.model.problem.ProblemFix
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import kotlinx.coroutines.CancellationException
import java.util.*

fun ProblemFix.Part.Change.ijRange(): TextRange = TextRange(range.start, range.endExclusive)

fun String.trimToNull(): String? = trim().takeIf(String::isNotBlank)

fun <T> Collection<T>.toLinkedSet() = LinkedSet<T>(this)

typealias LinkedSet<T> = LinkedHashSet<T>

val IntRange.length
  get() = endInclusive - start + 1


fun <T> Enumeration<T>.toSet() = toList().toSet()


inline fun <R> catching(block: () -> R): Result<R> {
  try {
    return Result.success(block())
  }
  catch (exception: CancellationException) {
    throw exception
  }
  catch (exception: ProcessCanceledException) {
    throw exception
  }
  catch (exception: Throwable) {
    return Result.failure(exception)
  }
}