// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck

internal class ToolWindowEditorTabPreCloseCheck : VirtualFilePreCloseCheck {
  override fun canCloseFile(file: VirtualFile): Boolean = canCloseFiles(listOf(file))

  override fun canCloseFiles(files: Collection<VirtualFile>): Boolean = filterFilesToClose(files).size == files.size

  override fun filterFilesToClose(files: Collection<VirtualFile>): Collection<VirtualFile> {
    val toolWindowTabFiles = files.filterIsInstance<ToolWindowEditorTabFile>()
    if (toolWindowTabFiles.isEmpty()) {
      return files
    }

    val closableToolWindowTabFiles = collectClosableFiles(toolWindowTabFiles)

    return files.filter { file ->
      file !is ToolWindowEditorTabFile || file in closableToolWindowTabFiles
    }
  }

  private fun collectClosableFiles(
    files: Collection<ToolWindowEditorTabFile>,
  ): Set<ToolWindowEditorTabFile> {
    val closableFiles = mutableSetOf<ToolWindowEditorTabFile>()
    val sessionsByGroup = LinkedHashMap<SessionGroup, MutableList<ToolWindowEditorTabSession>>()

    for (file in files) {
      val session = findSession(file)
      val support = ToolWindowEditorTabSupportUtil.getSupport(file.toolWindowId)

      if (session == null || support == null) {
        closableFiles += file
        continue
      }

      val group = SessionGroup(project = session.project, support = support)

      sessionsByGroup.getOrPut(group) { mutableListOf() } += session
    }

    for ((group, sessions) in sessionsByGroup) {
      val closableContents = group.support
        .filterTabsToClose(group.project, sessions.map { it.content })
        .toSet()

      sessions
        .filter { it.content in closableContents }
        .mapTo(closableFiles) { it.file }
    }

    return closableFiles
  }

  private fun findSession(file: ToolWindowEditorTabFile): ToolWindowEditorTabSession? {
    return ProjectManager.getInstance().openProjects
      .asSequence()
      .filterNot { it.isDisposed }
      .firstNotNullOfOrNull { project ->
        ToolWindowEditorTabManager.getInstance(project).getSession(file)
      }
  }

  private data class SessionGroup(
    val project: Project,
    val support: ToolWindowEditorTabSupport,
  )
}
