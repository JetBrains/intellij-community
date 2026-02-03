// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import javax.accessibility.Accessible

interface AccessibilityAudit {
  val failedInspections: MutableList<UiInspectorAccessibilityInspection>
  fun runAccessibilityTests(a: Accessible?)
  fun clearAccessibilityTestsResult()
}