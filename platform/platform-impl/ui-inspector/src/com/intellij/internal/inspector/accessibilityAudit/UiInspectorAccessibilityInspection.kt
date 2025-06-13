// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import com.intellij.icons.AllIcons
import com.intellij.internal.InternalActionsBundle
import org.jetbrains.annotations.ApiStatus
import javax.accessibility.Accessible
import javax.accessibility.AccessibleRole
import javax.swing.Icon

@ApiStatus.Internal
@ApiStatus.Experimental
interface UiInspectorAccessibilityInspection {
  val propertyName: String

  val description: String
    get() {
      val key = "ui.inspector.accessibility.audit.${this.javaClass.simpleName}.description"
      return if (accessibleRole != null) InternalActionsBundle.message(key, accessibleRole)
      else InternalActionsBundle.message(key)
    }

  //  Inspections that need to include the information about accessible role in the description can override this property
  var accessibleRole: AccessibleRole?
    get() = null
    set(_) {}

  val severity: Severity

  val icon: Icon
    get() = when (severity) {
      Severity.WARNING -> AllIcons.General.Warning
      Severity.RECOMMENDATION -> AllIcons.General.Information
      Severity.ERROR -> AllIcons.General.Error
    }

  fun passesInspection(accessible: Accessible?): Boolean
}

@ApiStatus.Internal
@ApiStatus.Experimental
enum class Severity {
  WARNING,
  RECOMMENDATION,
  ERROR
}
