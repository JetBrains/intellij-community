// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleNameAndDescriptionNotEqualInspection : UiInspectorAccessibilityInspection {
  override val propertyName: String = "AccessibleDescription"
  override val severity: Severity = Severity.WARNING

  override fun passesInspection(context: AccessibleContext): Boolean {
    if (context.accessibleName != null && context.accessibleDescription != null && context.accessibleName.isNotEmpty() && context.accessibleDescription.isNotEmpty()) {
      return context.accessibleName != context.accessibleDescription
    }
    return true
  }
}
