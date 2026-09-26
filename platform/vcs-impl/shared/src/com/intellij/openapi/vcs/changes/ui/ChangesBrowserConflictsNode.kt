// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ConflictsResolutionService
import com.intellij.openapi.vcs.merge.MergeResolveActionContext
import com.intellij.openapi.vcs.merge.MergeResolveActionPresentation
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.openapi.vcs.merge.MergeResolveActionSupport
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JTree

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
  override fun render(tree: JTree, renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val contributedActions = getContributedActions(tree)
    renderer.append(VcsBundle.message("changes.nodetitle.merge.conflicts"), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    renderer.append(FontUtil.spaceAndThinSpace(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
    renderer.append(VcsBundle.message("changes.nodetitle.merge.conflicts.resolve.link.label"), SimpleTextAttributes.LINK_BOLD_ATTRIBUTES, Runnable { showResolveConflictsDialog() })
    contributedActions.forEach { actionPresentation ->
      renderer.append(" / ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      if (actionPresentation.isEnabled) {
        renderer.append(actionPresentation.text, SimpleTextAttributes.LINK_BOLD_ATTRIBUTES, Runnable {
          performContributedAction(tree, actionPresentation.provider)
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

  private fun getContributedActions(tree: JTree): List<ConflictNodeActionPresentation> {
    val mergeContext = createMergeContext(tree) ?: return emptyList()
    return MergeResolveActionSupport.collectActionPresentations(mergeContext, tree, CONFLICTS_NODE_ACTION_PLACE)
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

  private fun performContributedAction(tree: JTree, provider: MergeResolveActionProvider) {
    val mergeContext = createMergeContext(tree) ?: return
    MergeResolveActionSupport.performAction(provider, mergeContext, tree, CONFLICTS_NODE_ACTION_PLACE)
  }

  private fun createMergeContext(tree: JTree): MergeResolveActionContext? {
    if (collectConflictFiles(this).isEmpty()) return null

    return MergeResolveActionContext(
      project = project,
      selectionHintFilesProvider = { collectSelectionHintFiles(tree) },
    )
  }

  private fun collectSelectionHintFiles(tree: JTree): List<VirtualFile> {
    val selectedNodes = tree.selectionPaths
                        ?.mapNotNull { path -> path.lastPathComponent as? ChangesBrowserNode<*> }
                        .orEmpty()
    // The node itself stands for every conflict under it. A click on a link of this row selects the row before the link
    // runs, so a user who clicks the link has always selected this node, whatever was selected before the press.
    if (selectedNodes.any { node -> node === this }) return collectConflictFiles(this)
    val files = LinkedHashSet<VirtualFile>()
    selectedNodes
      .filter { node -> node.isUnderTag(CONFLICTS_NODE_TAG) }
      .forEach { node -> files.addAll(collectConflictFiles(node)) }
    return files.toList()
  }

  override fun getTextPresentation(): String {
    return VcsBundle.message("changes.nodetitle.merge.conflicts")
  }

  override fun getSortWeight(): Int = CONFLICTS_SORT_WEIGHT
}

private fun collectConflictFiles(node: ChangesBrowserNode<*>): List<VirtualFile> {
  val files = LinkedHashSet<VirtualFile>()
  for (userObject in node.traverseObjectsUnder()) {
    when (userObject) {
      is VirtualFile -> if (userObject.isValid) {
        files.add(userObject)
      }
      is Change -> resolveConflictFile(userObject)?.let(files::add)
    }
  }
  return files.toList()
}

private fun resolveConflictFile(change: Change): VirtualFile? {
  val afterFile = change.afterRevision?.file?.virtualFile
  if (afterFile != null && afterFile.isValid) {
    return afterFile
  }
  val beforeFile = change.beforeRevision?.file?.virtualFile
  return beforeFile?.takeIf(VirtualFile::isValid)
}
