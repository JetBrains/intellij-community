// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.diagnostic.errorsDialog

import com.intellij.diagnostic.AbstractMessage
import com.intellij.diagnostic.MessagePool
import com.intellij.diagnostic.RecoveredThrowable
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.ProblematicPluginInfo
import com.intellij.openapi.extensions.PluginId

/**
 * Describes a group of errors with the same stracktrace in [com.intellij.diagnostic.IdeErrorsDialog].
 */
internal class ErrorMessageCluster(
  val messages: List<AbstractMessage>,
  val pluginId: PluginId?,
  val pluginInfo: ProblematicPluginInfo?,
  val submitter: ErrorReportSubmitter?
) {
  val first = messages.first()

  @Volatile
  var detailsText: String? = detailsText()

  private fun detailsText(): String? {
    val t = first.throwable
    if (t is MessagePool.TooManyErrorsException) {
      return t.message
    }
    val userMessage = first.message
    val stacktrace = first.throwableText
    return if (userMessage.isNullOrBlank()) stacktrace else "${userMessage}\n\n${stacktrace}"
  }

  val isUnsent: Boolean get() = !first.isSubmitted && !first.isSubmitting

  val canSubmit: Boolean get() = submitter != null && isUnsent

  fun decouple(): Pair<String?, Throwable>? {
    val detailsText = detailsText!!
    val originalThrowableText = first.throwableText
    val originalThrowableClass = first.throwable.javaClass.name

    val p1 = detailsText.indexOf(originalThrowableText)
    if (p1 >= 0) {
      val message = detailsText.substring(0, p1).trim { it <= ' ' }.takeIf(String::isNotEmpty)
      return message to first.throwable
    }

    if (detailsText.startsWith(originalThrowableClass)) {
      return null to RecoveredThrowable.fromString(detailsText)
    }

    val p2 = detailsText.indexOf('\n' + originalThrowableClass)
    if (p2 >= 0) {
      val message = detailsText.substring(0, p2).trim { it <= ' ' }.takeIf(String::isNotEmpty)
      return message to RecoveredThrowable.fromString(detailsText.substring(p2 + 1))
    }

    return null
  }
}