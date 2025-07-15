// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.accessibilityAudit

import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.accessibility.AccessibleState

internal fun AccessibleContext.isInteractive(): Boolean {
  return this.accessibleRole in arrayOf(AccessibleRole.CHECK_BOX,
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
                                        AccessibleRole.COMBO_BOX,
                                        AccessibleRole.SLIDER)
}

internal fun AccessibleContext.isVisibleAndEnabled(): Boolean {
  return this.accessibleStateSet.contains(AccessibleState.ENABLED) &&
         this.accessibleStateSet.contains(AccessibleState.VISIBLE) &&
         this.accessibleStateSet.contains(AccessibleState.SHOWING)
}