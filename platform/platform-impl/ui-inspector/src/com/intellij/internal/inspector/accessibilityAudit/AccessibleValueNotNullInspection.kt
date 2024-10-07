// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleValueNotNullInspection : UiInspectorAccessibilityInspection {
  override fun passesInspection(context: AccessibleContext): Boolean {
    if (context.accessibleRole in arrayOf(AccessibleRole.PROGRESS_BAR,
                                          AccessibleRole.SPIN_BOX,
                                          AccessibleRole.SLIDER,
                                          AccessibleRole.SCROLL_BAR)
    ) {
      return context.accessibleValue != null
    }
    return true
  }
}

// progress bar, spin box, slider, scroll bar