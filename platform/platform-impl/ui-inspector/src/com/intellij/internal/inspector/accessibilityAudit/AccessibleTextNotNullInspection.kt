// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import com.intellij.internal.InternalActionsBundle
import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleTextNotNullInspection : UiInspectorAccessibilityInspection {
  override val propertyName: String = "AccessibleText"
  override val severity: Severity = Severity.WARNING

  override fun passesInspection(context: AccessibleContext): Boolean {
    if (context.accessibleRole == AccessibleRole.TEXT || context.accessibleRole == AccessibleRole.PASSWORD_TEXT) {
      return context.accessibleText != null
    }
    return true
  }
}
