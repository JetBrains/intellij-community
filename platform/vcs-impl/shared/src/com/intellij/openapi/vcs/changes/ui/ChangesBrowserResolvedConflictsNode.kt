// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus

@JvmField
internal val RESOLVED_CONFLICTS_NODE_TAG: ChangesBrowserNode.Tag = object : ChangesBrowserNode.Tag {}

@ApiStatus.Internal
class ChangesBrowserResolvedConflictsNode(
  val project: Project,
  private val attributes: SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES,
) : TagChangesBrowserNode(RESOLVED_CONFLICTS_NODE_TAG, attributes, true) {
  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.append(VcsBundle.message("changes.nodetitle.resolved.conflicts"), attributes)
  }

  override fun getSortWeight(): Int = RESOLVED_CONFLICTS_SORT_WEIGHT
}
