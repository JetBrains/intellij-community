// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import com.intellij.internal.InternalActionsBundle
import org.jetbrains.annotations.ApiStatus
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState

@ApiStatus.Internal
@ApiStatus.Experimental
class AccessibleStateSetContainsFocusableInspection : UiInspectorAccessibilityInspection {
  override val propertyName: String = "AccessibleStateSet"
  override val severity: Severity = Severity.WARNING

  override fun passesInspection(context: AccessibleContext): Boolean {
    if ((context.accessibleStateSet.contains(AccessibleState.ENABLED) &&
         context.accessibleStateSet.contains(AccessibleState.VISIBLE) &&
         context.accessibleStateSet.contains(AccessibleState.SHOWING)) &&
        context.accessibleRole in arrayOf(AccessibleRole.CHECK_BOX,
                                          AccessibleRole.PUSH_BUTTON,
                                          AccessibleRole.RADIO_BUTTON,
                                          AccessibleRole.TOGGLE_BUTTON,
                                          AccessibleRole.LIST,
                                          AccessibleRole.LIST_ITEM,
                                          AccessibleRole.TABLE,
                                          AccessibleRole.TREE,
                                          AccessibleRole.PAGE_TAB_LIST,
                                          AccessibleRole.TEXT,
                                          AccessibleRole.PASSWORD_TEXT,
                                          AccessibleRole.HYPERLINK,
                                          AccessibleRole.POPUP_MENU,
                                          AccessibleRole.SLIDER)) {
      return context.accessibleStateSet.contains(AccessibleState.FOCUSABLE)
    }
    return true
  }
}
