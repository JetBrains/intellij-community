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
 * This field is recorded only if user enabled the data sharing.
 *
 * Use [LlmFieldKind.TEXT] when the value is expected to be natural-language text,
 * even when it may embed code blocks. Use [LlmFieldKind.CODE] when the value is expected to be
 * source code.
 */
@ApiStatus.Internal
data class RequestPrivateData(
  @param:NonNls override val name: String,
  val kind: LlmFieldKind,
) : StringEventField(name) {
  init {
    RequestPrivateDataSupport.checkFieldNameIsNotReservedForCheckedFileLogging(name)
  }

  @Deprecated(
    "Specify LlmFieldKind explicitly so PII filtering can pick the right rule. " +
      "Use LlmFieldKind.TEXT for natural-language text (which may contain code blocks); " +
      "use LlmFieldKind.CODE for source code.",
    ReplaceWith("RequestPrivateData(name, LlmFieldKind.TEXT)"),
  )
  @Suppress("DEPRECATION")
  constructor(@NonNls name: String) : this(name, LlmFieldKind.UNSPECIFIED)

  override val validationRule: List<String>
    get() = RequestPrivateDataSupport.validationRule(kind)

  override fun addData(fuData: FeatureUsageData, value: String?) {
    RequestPrivateDataSupport.addData(fuData, name, value)
  }
}

/**
 * File-backed content must be checked with [checkFileContentForLogging] before it can be added to analytics.
 */
@ApiStatus.Internal
class CheckedFilePrivateData @JvmOverloads constructor(
  @NonNls fieldName: String,
  val kind: LlmFieldKind = LlmFieldKind.TEXT,
) : PrimitiveEventField<CheckedFileContent?>() {
  override val name: String = fieldName
  override val validationRule: List<String>
    get() = RequestPrivateDataSupport.validationRule(kind)

  override fun addData(fuData: FeatureUsageData, value: CheckedFileContent?) {
    RequestPrivateDataSupport.addData(fuData, name, value?.content)
  }
}

/**
 * Distinguishes LLM-generated field values that carry source code from those that carry
 * natural-language or identifier-shaped text. Selects which accept-all validation rule
 * ([TrueValidationRuleCode] vs [TrueValidationRuleText]) the field reports.
 */
@ApiStatus.Internal
enum class LlmFieldKind { TEXT,
  CODE,
  @Deprecated("Used for backward compatibility only")
  UNSPECIFIED
}

@ApiStatus.Internal
class CheckedFileContent private constructor(internal val content: String) {
  companion object {
    internal fun create(content: String): CheckedFileContent = CheckedFileContent(content)
  }
}

@ApiStatus.Internal
fun checkFileContentForLogging(filePath: String?, content: String?): CheckedFileContent? {
  if (content == null || isDangerousFileForLogging(filePath)) {
    return null
  }
  return CheckedFileContent.create(content)
}

@ApiStatus.Internal
fun checkFileContentForLogging(filePaths: Iterable<String?>, content: String?): CheckedFileContent? {
  if (content == null || filePaths.any(::isDangerousFileForLogging)) {
    return null
  }
  return CheckedFileContent.create(content)
}

@ApiStatus.Internal
fun RequestPrivateData.with(value: CheckedFileContent?): EventPair<String?>? {
  return value?.let { this.with(it.content) }
}

@get:ApiStatus.Internal
val checkedFileLoggingFieldNames: Set<String>
  get() = RequestPrivateDataSupport.checkedFileLoggingFieldNames

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

private object RequestPrivateDataSupport {
  // Keep this list limited to field IDs that are dedicated to file-backed content.
  // Shared IDs such as "context" are handled at call sites because they also log non-file strings.
  val checkedFileLoggingFieldNames: Set<String> = setOf(
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

  @Suppress("DEPRECATION")
  fun validationRule(kind: LlmFieldKind): List<String> {
    val ruleClass = when (kind) {
      LlmFieldKind.TEXT -> TrueValidationRuleText::class.java
      LlmFieldKind.CODE -> TrueValidationRuleCode::class.java
      LlmFieldKind.UNSPECIFIED -> TrueValidationRuleText::class.java
    }
    return listOf("{util#${CustomValidationRule.getRuleId(ruleClass)}}")
  }

  fun checkFieldNameIsNotReservedForCheckedFileLogging(fieldName: String) {
    require(fieldName !in checkedFileLoggingFieldNames) {
      "Field '$fieldName' stores file-backed content. Use CheckedFilePrivateData with checkFileContentForLogging()."
    }
  }

  fun addData(fuData: FeatureUsageData, fieldName: String, value: String?) {
    if (value != null && TraceRawDataSharing.isRawDataLoggingAllowed()) {
      fuData.addData(fieldName, value)
    }
  }
}
