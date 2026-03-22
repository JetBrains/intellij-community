// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.toolwindow.ui

import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_PROJECT_PATH_CONTEXT_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptProjectPathContext
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.model.archiveThreadTargetKey
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.toolwindow.actions.AgentSessionsTreePopupDataKeys
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.toolwindow.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.archiveTargetFromThreadNode
import com.intellij.agent.workbench.sessions.toolwindow.tree.copyPathForSessionTreeId
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.TreePath

internal class AgentSessionsTreeDataContextProvider(
  private val project: Project,
  private val tree: Tree,
  private val nodeResolver: (SessionTreeId) -> SessionTreeNode?,
  private val popupActionContextProvider: () -> AgentSessionsTreePopupActionContext?,
) {
  fun uiDataSnapshot(sink: DataSink) {
    val selectedTreeId = selectedTreeId()
    val selectedTreeNode = selectedTreeId?.let(nodeResolver)
    val selectedCopyFiles = selectedCopyVirtualFiles()
    sink[AgentSessionsTreePopupDataKeys.CONTEXT] = resolveArchiveActionContext(
      popupActionContext = popupActionContextProvider(),
      project = project,
      selectedTreeId = selectedTreeId,
      selectedTreeNode = selectedTreeNode,
      selectedArchiveTargets = selectedArchiveTargets(),
    )
    sink[AGENT_PROMPT_PROJECT_PATH_CONTEXT_DATA_KEY] = resolvePromptProjectPathContext(selectedTreeId, selectedTreeNode)
    sink[CommonDataKeys.VIRTUAL_FILE_ARRAY] = selectedCopyFiles.takeIf { it.isNotEmpty() }?.toTypedArray()
    sink[CommonDataKeys.VIRTUAL_FILE] = selectedCopyFiles.firstOrNull()
    sink.lazyValue(CommonDataKeys.PSI_ELEMENT) { dataProvider ->
      val copyFiles = dataProvider[CommonDataKeys.VIRTUAL_FILE_ARRAY]?.toList() ?: selectedCopyFiles
      selectedCopyPsiItems(copyFiles).singleOrNull()
    }
    sink.lazyValue(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY) { dataProvider ->
      val copyFiles = dataProvider[CommonDataKeys.VIRTUAL_FILE_ARRAY]?.toList() ?: selectedCopyFiles
      selectedCopyPsiItems(copyFiles).takeIf { it.isNotEmpty() }?.toTypedArray()
    }
  }

  fun selectedArchiveTargets(): List<ArchiveThreadTarget> {
    val targetsByKey = LinkedHashMap<String, ArchiveThreadTarget>()
    selectedTreeIds().forEach { id ->
      val threadNode = nodeResolver(id) as? SessionTreeNode.Thread ?: return@forEach
      val target = archiveTargetFromThreadNode(id, threadNode)
      targetsByKey.putIfAbsent(archiveThreadTargetKey(target), target)
    }
    return targetsByKey.values.toList()
  }

  private fun selectedTreeId(): SessionTreeId? {
    return idFromPath(TreeUtil.getSelectedPathIfOne(tree))
  }

  private fun selectedTreeIds(): List<SessionTreeId> {
    val paths = tree.selectionPaths ?: return emptyList()
    return paths.mapNotNull { path -> idFromPath(path) }.distinct()
  }

  private fun idFromPath(path: TreePath?): SessionTreeId? {
    return path?.lastPathComponent?.let(::extractSessionTreeId)
  }

  private fun selectedCopyVirtualFiles(): List<VirtualFile> {
    val virtualFileManager = VirtualFileManager.getInstance()
    return selectedTreeIds()
      .asSequence()
      .mapNotNull(::copyPathForSessionTreeId)
      .mapNotNull(::parseAgentWorkbenchPathOrNull)
      .mapNotNull { path -> virtualFileManager.findFileByNioPath(path) }
      .distinct()
      .toList()
  }

  private fun selectedCopyPsiItems(selectedCopyFiles: List<VirtualFile>): List<PsiFileSystemItem> {
    return selectedCopyFiles
      .asSequence()
      .mapNotNull { file -> PsiUtilCore.findFileSystemItem(project, file) }
      .distinct()
      .toList()
  }

  private fun resolvePromptProjectPathContext(
    selectedTreeId: SessionTreeId?,
    selectedTreeNode: SessionTreeNode?,
  ): AgentPromptProjectPathContext? {
    val treeId = selectedTreeId ?: return null
    val treeNode = selectedTreeNode ?: return null
    val path = when (treeId) {
      is SessionTreeId.Project -> treeId.path
      is SessionTreeId.Thread -> treeId.projectPath
      is SessionTreeId.SubAgent -> treeId.projectPath
      is SessionTreeId.Warning -> treeId.projectPath
      is SessionTreeId.Error -> treeId.projectPath
      is SessionTreeId.Empty -> treeId.projectPath
      SessionTreeId.MoreProjects -> null
      is SessionTreeId.MoreThreads -> treeId.projectPath
      is SessionTreeId.Worktree -> treeId.worktreePath
      is SessionTreeId.WorktreeThread -> treeId.worktreePath
      is SessionTreeId.WorktreeSubAgent -> treeId.worktreePath
      is SessionTreeId.WorktreeWarning -> treeId.worktreePath
      is SessionTreeId.WorktreeMoreThreads -> treeId.worktreePath
      is SessionTreeId.WorktreeError -> treeId.worktreePath
    } ?: return null
    val displayName = when (treeNode) {
      is SessionTreeNode.Project -> treeNode.project.name
      is SessionTreeNode.Thread -> treeNode.project.name
      is SessionTreeNode.SubAgent -> treeNode.project.name
      is SessionTreeNode.Error -> treeNode.project.name
      is SessionTreeNode.Empty -> treeNode.project.name
      is SessionTreeNode.MoreThreads -> treeNode.project.name
      is SessionTreeNode.Worktree -> treeNode.worktree.name
      is SessionTreeNode.Warning,
      is SessionTreeNode.MoreProjects -> null
    }
    return AgentPromptProjectPathContext(path = path, displayName = displayName)
  }
}
