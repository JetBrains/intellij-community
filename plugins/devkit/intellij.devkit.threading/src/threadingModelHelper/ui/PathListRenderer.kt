// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import com.intellij.util.ui.UIUtil
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import javax.swing.ListCellRenderer

/**
 * Renders an [ExecutionPath] as one row: the requirement first, then the method chain.
 * The requirement comes first because the list cuts a long chain at the right edge.
 */
internal fun createPathListRenderer(): ListCellRenderer<ExecutionPath> = listCellRenderer {
  val path = value
  val requirement = path.lockRequirement
  icon(requirementIcon(requirement))
  text(requirementLabel(requirement)) {
    attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
  }
  text(path.methodChain.joinToString(" ${UIUtil.rightArrow()} ") { shortCallText(it) }) {
    if (path.isSpeculative) {
      foreground = greyForeground
    }
  }
}
