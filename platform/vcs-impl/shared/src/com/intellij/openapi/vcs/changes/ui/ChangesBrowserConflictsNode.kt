// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ConflictsResolutionService
import com.intellij.openapi.vcs.merge.MergeResolveActionPresentation
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.openapi.vcs.merge.MergeResolveActionSupport
import com.intellij.openapi.vcs.merge.MergeResolveWithAgentContext
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

private const val CONFLICTS_NODE_ACTION_PLACE = "ChangesView.ConflictsNode"

private data class ConflictNodeActionPresentation(
  val provider: MergeResolveActionProvider,
  val text: @Nls String,
  val description: @Nls String?,
  val isEnabled: Boolean,
)

@JvmField
internal val CONFLICTS_NODE_TAG: ChangesBrowserNode.Tag = object : ChangesBrowserNode.Tag {}

@ApiStatus.Internal
class ChangesBrowserConflictsNode(val project: Project)
  : TagChangesBrowserNode(CONFLICTS_NODE_TAG, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, true) {
  @Suppress("Nls")
  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val contributedActions = getContributedActions()
    renderer.append(VcsBundle.message("changes.nodetitle.merge.conflicts"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    renderer.append(FontUtil.spaceAndThinSpace(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    renderer.append(VcsBundle.message("changes.nodetitle.merge.conflicts.resolve.link.label"), SimpleTextAttributes.LINK_BOLD_ATTRIBUTES, Runnable { showResolveConflictsDialog() })
    contributedActions.forEach { actionPresentation ->
      renderer.append(" / ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      if (actionPresentation.isEnabled) {
        renderer.append(actionPresentation.text, SimpleTextAttributes.LINK_BOLD_ATTRIBUTES, Runnable {
          performContributedAction(actionPresentation.provider)
        })
      }
      else {
        renderer.append(actionPresentation.text, SimpleTextAttributes.GRAYED_BOLD_ATTRIBUTES)
      }
    }

    val tooltip: @Nls String? = contributedActions
      .asSequence()
      .filterNot(ConflictNodeActionPresentation::isEnabled)
      .mapNotNull(ConflictNodeActionPresentation::description)
      .distinct()
      .joinToString("\n")
      .takeIf(String::isNotEmpty)
    renderer.toolTipText = tooltip
  }

  private fun showResolveConflictsDialog() {
    project.service<ConflictsResolutionService>().showConflictResolutionDialog(allChangesUnder)
  }

  private fun getContributedActions(): List<ConflictNodeActionPresentation> {
    val mergeContext = createMergeContext() ?: return emptyList()
    return MergeResolveActionSupport.collectActionPresentations(mergeContext, null, CONFLICTS_NODE_ACTION_PLACE)
      .map(::createActionPresentation)
  }

  private fun createActionPresentation(presentation: MergeResolveActionPresentation): ConflictNodeActionPresentation {
    return ConflictNodeActionPresentation(
      provider = presentation.provider,
      text = presentation.text,
      description = presentation.description,
      isEnabled = presentation.isEnabled,
    )
  }

  private fun performContributedAction(provider: MergeResolveActionProvider) {
    val mergeContext = createMergeContext() ?: return
    MergeResolveActionSupport.performAction(provider, mergeContext, null, CONFLICTS_NODE_ACTION_PLACE)
  }

  private fun createMergeContext(): MergeResolveWithAgentContext? {
    val files = LinkedHashSet<com.intellij.openapi.vfs.VirtualFile>()
    iterateFilesUnder().forEach(files::add)
    for (change in allChangesUnder) {
      change.afterRevision?.file?.virtualFile?.takeIf { file -> file.isValid }?.let(files::add)
      change.beforeRevision?.file?.virtualFile?.takeIf { file -> file.isValid }?.let(files::add)
    }
    if (files.isEmpty()) return null

    return MergeResolveWithAgentContext(project = project, files = files.toList())
  }

  override fun getTextPresentation(): String {
    return VcsBundle.message("changes.nodetitle.merge.conflicts")
  }

  override fun getSortWeight(): Int = CONFLICTS_SORT_WEIGHT
}
