// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleState

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleNameNotEmptyForFocusableComponentsInspection : UiInspectorAccessibilityInspection {
  override val propertyName: String = "AccessibleName"
  override val severity: Severity = Severity.WARNING

  override fun passesInspection(context: AccessibleContext): Boolean {
    if (context.isVisibleAndEnabled() && context.accessibleStateSet.contains(AccessibleState.FOCUSABLE) && context.isInteractive()) {
      return context.accessibleName != null && !context.accessibleName.isEmpty()
    }
    return true
  }
}
