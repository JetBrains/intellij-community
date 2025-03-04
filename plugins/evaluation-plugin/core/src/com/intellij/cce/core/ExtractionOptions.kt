package com.intellij.cce.core

import com.intellij.cce.evaluable.METHOD_NAME_PROPERTY

/**
 * ExtractionOptions decouples language-specific details from the API call extraction logic.
 * For example:
 *  In static languages (e.g., Java/PHP), the method name narrows the search to a specific function.
 *  In dynamic languages (e.g., Python), the extraction may ignore the method name entirely.
 */
data class ExtractionOptions(
  val methodName: String? = null
)

fun TokenProperties.extractionOptions(): ExtractionOptions {
  return ExtractionOptions(
    methodName = this.additionalProperty(METHOD_NAME_PROPERTY)
  )
}
