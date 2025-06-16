// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.jetbrains.annotations.ApiStatus
import javax.accessibility.Accessible
import javax.accessibility.AccessibleRole

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleValueNotNullInspection : UiInspectorAccessibilityInspection {
  override val propertyName: String = "AccessibleValue"
  override val severity: Severity = Severity.WARNING
  override var accessibleRole: AccessibleRole? = null

  override fun passesInspection(accessible: Accessible?): Boolean {
    val context = accessible?.accessibleContext ?: return true
    if (context.accessibleRole in arrayOf(AccessibleRole.PROGRESS_BAR,
                                          AccessibleRole.SPIN_BOX,
                                          AccessibleRole.SLIDER,
                                          AccessibleRole.CHECK_BOX,
                                          AccessibleRole.RADIO_BUTTON,
                                          AccessibleRole.SCROLL_BAR)
    ) {
      val result = context.accessibleValue != null
      if (!result) accessibleRole = context.accessibleRole
      return result
    }
    return true
  }
}
