// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck

internal class ToolWindowEditorTabPreCloseCheck : VirtualFilePreCloseCheck {
  override fun canCloseFile(file: VirtualFile): Boolean = canCloseFiles(listOf(file))

  override fun canCloseFiles(files: Collection<VirtualFile>): Boolean = filterFilesToClose(files).size == files.size

  override fun filterFilesToClose(files: Collection<VirtualFile>): Collection<VirtualFile> {
    val closableToolWindowTabFiles = buildSet {
      files
        .filterIsInstance<ToolWindowEditorTabFile>()
        .groupBy { it.toolWindowId }
        .values
        .forEach { tabFiles ->
          val sampleTabFile = tabFiles.first()
          val support = ToolWindowEditorTabSupportUtil.getSupport(sampleTabFile.toolWindowId)

          if (support == null) {
            addAll(tabFiles)
            return@forEach
          }

          val closableContents = support.filterTabsToClose(sampleTabFile.project, tabFiles.map { it.content }).toHashSet()

          tabFiles.filterTo(this) { it.content in closableContents }
        }
    }

    return files.filter { it !is ToolWindowEditorTabFile || it in closableToolWindowTabFiles }
  }
}
