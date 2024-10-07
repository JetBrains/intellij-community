// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleState

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleNameNotEmptyForFocusableComponentsInspection : UiInspectorAccessibilityInspection {
  override fun passesInspection(context: AccessibleContext): Boolean {
    val states = context.accessibleStateSet
    val containsAll = states.contains(AccessibleState.ENABLED)
                      && states.contains(AccessibleState.FOCUSABLE)
                      && states.contains(AccessibleState.VISIBLE)
                      && states.contains(AccessibleState.SHOWING)
    if (containsAll) {
      return context.accessibleName != null && !context.accessibleName.isEmpty()
    }
    return true
  }
}
