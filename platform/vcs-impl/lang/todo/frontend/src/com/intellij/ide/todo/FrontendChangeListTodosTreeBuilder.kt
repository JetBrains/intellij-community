// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo

import com.intellij.ide.todo.model.TodoScope
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import javax.swing.JTree

@ApiStatus.Internal
class FrontendChangeListTodosTreeBuilder(
  tree: JTree,
  project: Project,
) : TodoTreeBuilder(tree, project) {

  private data class ChangeListFiles(val ids: List<VirtualFileId>, val files: Set<VirtualFile>)

  @Volatile
  private var myFiles: ChangeListFiles = ChangeListFiles(emptyList(), emptySet())

  internal fun setFiles(fileIds: List<VirtualFileId>) {
    val files = HashSet<VirtualFile>()
    for (fileId in fileIds) {
      val file = fileId.virtualFile()
      if (file != null) {
        files.add(file)
      }
    }
    myFiles = ChangeListFiles(fileIds, files)
  }

  internal fun isChangedFile(file: VirtualFile): Boolean {
    return myFiles.files.contains(file)
  }

  override fun getScope(): TodoScope {
    return TodoScope.ChangeList(myFiles.ids)
  }

  override fun createTreeStructure(): TodoTreeStructure {
    return FrontendChangeListTodosTreeStructure(myProject)
  }
}