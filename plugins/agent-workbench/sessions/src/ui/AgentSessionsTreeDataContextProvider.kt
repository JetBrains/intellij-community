// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.ui

import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupActionContext
import com.intellij.agent.workbench.sessions.actions.AgentSessionsTreePopupDataKeys
import com.intellij.agent.workbench.sessions.model.ArchiveThreadTarget
import com.intellij.agent.workbench.sessions.tree.SessionTreeId
import com.intellij.agent.workbench.sessions.tree.SessionTreeNode
import com.intellij.agent.workbench.sessions.tree.archiveTargetFromThreadNode
import com.intellij.agent.workbench.sessions.tree.copyPathForSessionTreeId
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
      val key = "${target.path}:${target.provider}:${target.threadId}"
      targetsByKey.putIfAbsent(key, target)
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
}
