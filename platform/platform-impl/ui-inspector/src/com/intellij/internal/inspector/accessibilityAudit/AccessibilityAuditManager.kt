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

  private val inspections by lazy {
    listOf(
      AccessibleActionNotNullInspection(),
      AccessibleEditableTextNotNullInspection(),
      AccessibleNameAndDescriptionNotEqualInspection(),
      AccessibleNameNotEmptyForFocusableComponentsInspection(),
      AccessibleNameNotEmptyForIcon(),
      AccessibleStateSetContainsFocusableInspection(),
      AccessibleTextNotNullInspection(),
      AccessibleValueNotNullInspection(),
      ImplementsAccessibleInterfaceInspection(),
      ComponentWithIconHasNonDefaultAccessibleNameInspection(),
    )
  }

  override fun runAccessibilityTests(a: Accessible?) {
    isRunning = true
    failedInspections.clear()
    inspections.filterTo(failedInspections) { !it.passesInspection(a) }
  }

  val severityCount: SeverityCount get() = SeverityCount.from(failedInspections)

  override fun clearAccessibilityTestsResult() {
    isRunning = false
    failedInspections.clear()
  }
}
