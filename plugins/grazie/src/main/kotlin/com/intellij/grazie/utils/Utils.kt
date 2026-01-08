// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import ai.grazie.gec.model.problem.ProblemFix
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import java.util.*

fun ProblemFix.Part.Change.ijRange(): TextRange = TextRange(range.start, range.endExclusive)
fun ai.grazie.text.TextRange.ijRange(): TextRange = TextRange(start, endExclusive)
fun ai.grazie.rules.tree.TextRange.ijRange(): TextRange = TextRange(start, end)
fun TextRange.aiRange(): ai.grazie.text.TextRange = ai.grazie.text.TextRange(startOffset, endOffset)

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

/**
 * Same as [runBlockingModal] but without [ModalTaskOwner].
 */
internal fun <T> runBlockingModalProcess(
  project: Project? = null,
  title: @NlsContexts.DialogTitle String,
  isCancellable: Boolean = true,
  block: suspend CoroutineScope.() -> T,
): T {
  return ProgressManager.getInstance().runProcessWithProgressSynchronously(
    ThrowableComputable { runBlockingCancellable(block) },
    title,
    isCancellable,
    project
  )
}

val isPromotionAllowed: Boolean
  get() {
    if (ApplicationInfoEx.getInstanceEx().isVendorJetBrains) return true
    val pluginId = PluginId.getId("com.intellij.marketplace")
    return PluginManagerCore.isLoaded(pluginId)
  }