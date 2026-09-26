// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.EditorHostedComponent
import java.awt.Component
import java.awt.Window

/**
 * Whether the input focus belongs to [content] and not to a component that [content] hosts.
 *
 * The walk goes up from [focusOwner]. It stops at the first of the two components, so a hosted component that owns
 * the input focus wins over a [content] above it. It also stops at a [Window], so the focus in an owned dialog or
 * popup does not count as the focus in [content].
 *
 * The walk reads the AWT hierarchy. Call it on the EDT.
 */
internal fun isInputFocusOwner(focusOwner: Component?, content: Component): Boolean {
  var component: Component? = focusOwner
  while (component != null) {
    if (component === content) {
      return true
    }
    if (component is EditorHostedComponent && component.isInputFocusOwner) {
      return false
    }
    component = if (component is Window) {
      null
    } else {
      component.parent
    }
  }
  return false
}
