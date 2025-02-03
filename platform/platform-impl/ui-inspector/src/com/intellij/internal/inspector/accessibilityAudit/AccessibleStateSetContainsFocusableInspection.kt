// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleState

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleStateSetContainsFocusableInspection : UiInspectorAccessibilityInspection {
  override val propertyName: String = "AccessibleStateSet"
  override val severity: Severity = Severity.WARNING

  override fun passesInspection(context: AccessibleContext): Boolean {
    if (context.isInteractive() && context.isVisibleAndEnabled()) {
      return context.accessibleStateSet.contains(AccessibleState.FOCUSABLE)
    }
    return true
  }
}
