// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.ListCellRenderer

private const val MAX_NAME_LEN = 35

internal fun createChangeListChooserRenderer(): ListCellRenderer<LocalChangeList?> {
  return listCellRenderer("") {
    val value = value
    val name = StringUtil.shortenTextWithEllipsis(value.getName().trim { it <= ' ' }, MAX_NAME_LEN, 0)
    text(name) {
      if (value.isDefault()) {
        attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
      }
    }
  }
}
