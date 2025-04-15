// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
data class SeverityCount(
  val errors: Int = 0,
  val warnings: Int = 0,
  val recommendations: Int = 0
) {
  val total: Int get() = errors + warnings + recommendations

  companion object {
    fun from(inspections: List<UiInspectorAccessibilityInspection>): SeverityCount {
      var errors = 0
      var warnings = 0
      var recommendations = 0

      inspections.forEach {
        when (it.severity) {
          Severity.ERROR -> errors++
          Severity.WARNING -> warnings++
          Severity.RECOMMENDATION -> recommendations++
        }
      }

      return SeverityCount(errors, warnings, recommendations)
    }
  }
}