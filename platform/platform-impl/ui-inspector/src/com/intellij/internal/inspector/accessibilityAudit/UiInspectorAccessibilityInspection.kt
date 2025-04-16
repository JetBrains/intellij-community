// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import com.intellij.internal.InternalActionsBundle
import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext

@ApiStatus.Internal
@ApiStatus.Experimental
interface UiInspectorAccessibilityInspection {
  val propertyName: String
  val description: String
    get() = InternalActionsBundle.message("ui.inspector.accessibility.audit.${this.javaClass.simpleName}.description")
  val severity: Severity
  fun passesInspection(context: AccessibleContext): Boolean
}

@ApiStatus.Internal
@ApiStatus.Experimental
enum class Severity {
  WARNING,
  RECOMMENDATION
}

