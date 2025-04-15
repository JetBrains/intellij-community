// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.jetbrains.annotations.ApiStatus
import javax.accessibility.Accessible

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibilityAuditManager : AccessibilityAudit {
  override val failedInspections: MutableList<UiInspectorAccessibilityInspection> = mutableListOf()
  var isRunning: Boolean = false
    private set

  override fun runAccessibilityTests(a: Accessible?) {
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
      AccessibleValueNotNullInspection(),
      ImplementsAccessibleInterfaceInspection()
    )
    for (inspection in inspections) {
      if (!inspection.passesInspection(a)) {
        failedInspections.add(inspection)
      }
    }
  }

  fun getSeverityCount() : SeverityCount {
    return SeverityCount.from(failedInspections)
  }

  override fun clearAccessibilityTestsResult() {
    isRunning = false
    failedInspections.clear()
  }
}
