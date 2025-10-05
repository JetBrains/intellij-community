// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import com.intellij.ui.SimpleColoredComponent
import org.jetbrains.annotations.ApiStatus
import javax.accessibility.Accessible
import javax.swing.JLabel

@ApiStatus.Internal
@ApiStatus.Experimental
class ComponentWithIconHasNonDefaultAccessibleNameInspection : UiInspectorAccessibilityInspection {
  override val propertyName: String = "AccessibleName"
  override val severity: Severity = Severity.RECOMMENDATION

  override fun passesInspection(accessible: Accessible?): Boolean {
    val (icon, text, accessibleName) = when (accessible) {
      is JLabel -> Triple(accessible.icon, accessible.text, accessible.accessibleContext?.accessibleName)
      is SimpleColoredComponent -> Triple(accessible.icon, accessible.toString(), accessible.accessibleContext?.accessibleName)
      else -> return true
    }
    if (icon == null || icon.iconWidth == 0 || icon.iconHeight == 0 || text.isNullOrEmpty() || accessibleName == null) return true
    return accessibleName != text
  }
}