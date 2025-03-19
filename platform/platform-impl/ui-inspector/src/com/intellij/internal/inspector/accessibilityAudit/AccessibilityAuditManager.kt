// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import javax.accessibility.AccessibleContext
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibilityAuditManager : AccessibilityAudit {
  override val failedInspections = mutableListOf<UiInspectorAccessibilityInspection>()
  var isRunning = false
    private set

  override fun runAccessibilityTests(ac: AccessibleContext) {
    isRunning = true
    failedInspections.clear()

    val inspections = listOf(
      AccessibleActionNotNullInspection(),
      AccessibleEditableTextNotNullInspection(),
      AccessibleNameAndDescriptionNotEqualInspection(),
      AccessibleNameNotEmptyForFocusableComponentsInspection(),
      AccessibleNameNotEmptyForIcon(),
      AccessibleStateSetContainsFocusableInspection(),
      AccessibleTextNotNullInspection(),
      AccessibleValueNotNullInspection()
    )

    for (inspection in inspections) {
      if (!inspection.passesInspection(ac)) {
        failedInspections.add(inspection)
      }
    }
  }

  override fun clearAccessibilityTestsResult() {
    isRunning = false
    failedInspections.clear()
  }
}