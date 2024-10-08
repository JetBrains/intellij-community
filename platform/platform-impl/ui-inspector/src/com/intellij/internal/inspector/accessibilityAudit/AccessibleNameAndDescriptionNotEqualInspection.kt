// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import com.intellij.internal.InternalActionsBundle
import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleNameAndDescriptionNotEqualInspection : UiInspectorAccessibilityInspection {
  override val propertyName: String = "AccessibleName and AccessibleDescription"
  override val severity: Severity = Severity.WARNING

  override fun passesInspection(context: AccessibleContext): Boolean {
    val name = context.accessibleName
    val description = context.accessibleDescription
    if (name != null && description != null && name.isNotEmpty() && description.isNotEmpty()) {
      return name != description
    }
    return true
  }
}
