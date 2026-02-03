// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.todo.TodoTreeBuilder
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TodoRemoteItemNode(
  project: Project,
  value: Value,
  builder: TodoTreeBuilder
) : BaseToDoNode<TodoRemoteItemNode.Value>(project, value, builder) {

  data class Value(
    val file: VirtualFile,
    val navigationOffset: Int,
    val length: Int,
    val line: Int,
    val lineText: String
  )

  override fun createPresentation(): PresentationData = PresentationData()

  override fun getFileCount(`val`: Value?): Int { return 1 }

  override fun getTodoItemCount(`val`: Value?): Int { return 1 }

  override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

  override fun update(presentation: PresentationData) {
    val v = value ?: return
    @NlsSafe val text = "${v.line + 1} : ${v.lineText}"
    presentation.presentableText = text
    // TODO add icons, parsing, highlighting...
  }

  override fun canNavigate(): Boolean = true

  override fun navigate(requestFocus: Boolean) {
    val v = value ?: return
    OpenFileDescriptor(project, v.file, v.navigationOffset).navigate(requestFocus)
  }

}