// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.trace

import com.intellij.ide.ConsentOptionsProvider
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.PrimitiveEventField
import com.intellij.internal.statistic.eventLog.events.StringEventField
import com.intellij.internal.statistic.eventLog.validator.rules.impl.CustomValidationRule
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly

/**
 * A field whose value is recorded as it is, without validation, and only when the user allows TRACE data collection.
 *
 * Use [TraceRawFieldKind.TEXT] when the value is expected to be natural-language text, even when it may embed code
 * blocks, and [TraceRawFieldKind.CODE] when it is expected to be source code.
 *
 * File-backed content needs [TraceRawFileField] instead, so that dangerous paths are excluded.
 */
@ApiStatus.Internal
data class TraceRawField(
  @param:NonNls override val name: String,
  val kind: TraceRawFieldKind,
) : StringEventField(name) {
  init {
    require(name !in reservedFileContentFieldNames) {
      "Field '$name' stores file-backed content. Use TraceRawFileField with checkFileContentForLogging()."
    }
  }

  @Deprecated(
    "Specify TraceRawFieldKind explicitly so PII filtering can pick the right rule. " +
      "Use TraceRawFieldKind.TEXT for natural-language text (which may contain code blocks); " +
      "use TraceRawFieldKind.CODE for source code.",
    ReplaceWith("TraceRawField(name, TraceRawFieldKind.TEXT)"),
  )
  @Suppress("DEPRECATION")
  constructor(@NonNls name: String) : this(name, TraceRawFieldKind.UNSPECIFIED)

  override val validationRule: List<String>
    get() = rawValidationRule(kind)

  override fun addData(fuData: FeatureUsageData, value: String?) {
    addRawData(fuData, name, value)
  }
}

/**
 * A raw field for content read from a file. The value can only be produced by [checkFileContentForLogging],
 * which drops content whose path looks sensitive.
 */
@ApiStatus.Internal
class TraceRawFileField @JvmOverloads constructor(
  @NonNls fieldName: String,
  val kind: TraceRawFieldKind = TraceRawFieldKind.TEXT,
) : PrimitiveEventField<TraceRawFileContent?>() {
  override val name: String = fieldName
  override val validationRule: List<String>
    get() = rawValidationRule(kind)

  override fun addData(fuData: FeatureUsageData, value: TraceRawFileContent?) {
    addRawData(fuData, name, value?.content)
  }
}

/**
 * Selects which accept-all validation rule a raw field reports, so that code-shaped and text-shaped values stay
 * distinguishable downstream.
 */
@ApiStatus.Internal
enum class TraceRawFieldKind { TEXT,
  CODE,
  @Deprecated("Used for backward compatibility only")
  UNSPECIFIED
}

@ApiStatus.Internal
class TraceRawFileContent private constructor(internal val content: String) {
  companion object {
    internal fun create(content: String): TraceRawFileContent = TraceRawFileContent(content)
  }
}

@ApiStatus.Internal
fun checkFileContentForLogging(filePath: String?, content: String?): TraceRawFileContent? {
  if (content == null || isDangerousFileForLogging(filePath)) {
    return null
  }
  return TraceRawFileContent.create(content)
}

@ApiStatus.Internal
fun checkFileContentForLogging(filePaths: Iterable<String?>, content: String?): TraceRawFileContent? {
  if (content == null || filePaths.any(::isDangerousFileForLogging)) {
    return null
  }
  return TraceRawFileContent.create(content)
}

@ApiStatus.Internal
fun TraceRawField.with(value: TraceRawFileContent?): EventPair<String?>? {
  return value?.let { this.with(it.content) }
}

/**
 * Field names dedicated to file-backed content, which therefore must be declared as [TraceRawFileField].
 * Shared names such as "context" are not listed, because they also carry strings that come from no file.
 */
@ApiStatus.Internal
val reservedFileContentFieldNames: Set<String> = setOf(
  "after",
  "before",
  "content",
  "diffs",
  "editable_region",
  "file_text",
  "inspectionsProblems",
  "messages",
  "output",
  "prefix",
  "selection",
  "suffix",
  "syntaxErrorsDescription",
)

/**
 * Suppresses raw values in automated runs that must not upload real code, such as evaluation and integration tests.
 */
@ApiStatus.Internal
object TraceRawDataSharing {
  @set:TestOnly
  @Volatile
  var isForciblyDisabledForTests: Boolean = false

  fun isRawDataLoggingAllowed(): Boolean {
    val application = ApplicationManager.getApplication() ?: return false
    if (application.isUnitTestMode) {
      return true
    }
    if (isForciblyDisabledForTests) {
      return false
    }
    val consentOptions = application.getService(ConsentOptionsProvider::class.java) ?: return false
    return consentOptions.isTraceDataCollectionAllowed
  }
}

@Suppress("DEPRECATION")
private fun rawValidationRule(kind: TraceRawFieldKind): List<String> {
  val ruleClass = when (kind) {
    TraceRawFieldKind.TEXT -> TraceRawTextValidationRule::class.java
    TraceRawFieldKind.CODE -> TraceRawCodeValidationRule::class.java
    TraceRawFieldKind.UNSPECIFIED -> TraceRawTextValidationRule::class.java
  }
  return listOf("{util#${CustomValidationRule.getRuleId(ruleClass)}}")
}

private fun addRawData(fuData: FeatureUsageData, fieldName: String, value: String?) {
  if (value != null && TraceRawDataSharing.isRawDataLoggingAllowed()) {
    fuData.addData(fieldName, value)
  }
}
