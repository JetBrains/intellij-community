// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleEditableTextNotNullInspection : UiInspectorAccessibilityInspection {
  override fun passesInspection(context: AccessibleContext): Boolean {
    if ((context.accessibleRole == AccessibleRole.TEXT ||
        context.accessibleRole == AccessibleRole.PASSWORD_TEXT) &&
        context.accessibleStateSet.contains(AccessibleState.EDITABLE)) {
      return context.accessibleEditableText != null
    }
    return true
  }
}