// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import javax.accessibility.AccessibleContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibilityAuditManager: AccessibilityAudit {
  private var accessibilityTestResult: AccessibilityTestResult = AccessibilityTestResult.NOT_RUNNING
  var isRunning = false
    private set

  override fun runAccessibilityTests(ac: AccessibleContext) {
    isRunning = true
    accessibilityTestResult = AccessibilityTestResult.PASS

    if (!AccessibleNameAndDescriptionNotEqualInspection().passesInspection(ac)) {
      accessibilityTestResult = AccessibilityTestResult.FAIL
    }

    if (!AccessibleNameNotEmptyForFocusableComponentsInspection().passesInspection(ac)) {
      accessibilityTestResult = AccessibilityTestResult.FAIL
    }

    if (!AccessibleActionNotNullInspection().passesInspection(ac)) {
      accessibilityTestResult = AccessibilityTestResult.FAIL
    }

    if (!AccessibleTextNotNullInspection().passesInspection(ac)) {
      accessibilityTestResult = AccessibilityTestResult.FAIL
    }

    if (!AccessibleEditableTextNotNullInspection().passesInspection(ac)) {
      accessibilityTestResult = AccessibilityTestResult.FAIL
    }

    if (!AccessibleValueNotNullInspection().passesInspection(ac)) {
      accessibilityTestResult = AccessibilityTestResult.FAIL
    }
  }

  override fun clearAccessibilityTestsResult() {
    isRunning = false
    accessibilityTestResult = AccessibilityTestResult.NOT_RUNNING
  }

  override fun getAccessibilityTestResult(): AccessibilityTestResult {
    return accessibilityTestResult
  }
}