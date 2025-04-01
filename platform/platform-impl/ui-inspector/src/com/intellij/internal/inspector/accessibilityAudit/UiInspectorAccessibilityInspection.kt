// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import com.intellij.icons.AllIcons
import com.intellij.internal.InternalActionsBundle
import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext
import javax.swing.Icon

@ApiStatus.Internal
@ApiStatus.Experimental
interface UiInspectorAccessibilityInspection {
  val propertyName: String
  val description: String
    get() = InternalActionsBundle.message("ui.inspector.accessibility.audit.${this.javaClass.simpleName}.description")
  val severity: Severity
  fun passesInspection(context: AccessibleContext): Boolean
  fun getIcon(): Icon {
    return when (severity) {
      Severity.WARNING -> AllIcons.General.Warning
      Severity.RECOMMENDATION -> AllIcons.General.InspectionsOK
    }
  }
}

@ApiStatus.Internal
@ApiStatus.Experimental
enum class Severity {
  WARNING,
  RECOMMENDATION
}

