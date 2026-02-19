// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import javax.accessibility.Accessible

class ImplementsAccessibleInterfaceInspection : UiInspectorAccessibilityInspection {
  override val propertyName: String = "Accessible"
  override val severity: Severity = Severity.ERROR

  override fun passesInspection(accessible: Accessible?): Boolean {
    return accessible != null
  }
}
