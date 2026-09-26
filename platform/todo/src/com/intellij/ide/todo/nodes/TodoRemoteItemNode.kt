// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.nodes

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.todo.HighlightedRegionProvider
import com.intellij.ide.todo.TodoTreeBuilder
import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.ui.HighlightedRegion
import com.intellij.usageView.UsageTreeColors
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NotNull
import java.awt.Font

@ApiStatus.Internal
class TodoRemoteItemNode(
  project: Project,
  value: Value,
  builder: TodoTreeBuilder
) : BaseToDoNode<TodoRemoteItemNode.Value>(project, value, builder), LeafTodoItemNode, HighlightedRegionProvider {

  data class Value(
    val file: VirtualFile,
    val navigationOffset: Int,
    val length: Int,
    val line: Int,
    val presentation: List<SerializableTextChunk>,
  )

  private fun checkValue() : Value {
    return checkNotNull(value) { "TodoRemoteItemNode value is null" }
  }

  override fun canRepresent(element: Any?): Boolean {
    if (element !is TodoRemoteItemNode) return false
    val v = value ?: return false
    val otherValue = element.value ?: return false
    return v.file == otherValue.file && v.navigationOffset == otherValue.navigationOffset
  }

  override fun contains(element: Any?): Boolean {
    return false
  }

  override fun createPresentation(): PresentationData = TodoItemNodePresentationData()

  override fun getHighlightedRegions(): List<HighlightedRegion> = (presentation as TodoItemNodePresentationData).highlightedRegions

  override fun getFileCount(`val`: Value?): Int { return 1 }

  override fun getTodoItemCount(`val`: Value?): Int { return 1 }

  override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()

  override fun update(presentation: PresentationData) {
    val data = presentation as TodoItemNodePresentationData
    data.highlightedRegions.clear()
    val v = value ?: return

    val prefix = "${v.line + 1} "
    val lineText = v.presentation.joinToString("") { it.text }
    data.presentableText = prefix + lineText
    data.highlightedRegions.add(
      HighlightedRegion(0, prefix.length, UsageTreeColors.NUMBER_OF_USAGES_ATTRIBUTES.toTextAttributes())
    )

    var offset = prefix.length
    for (chunk in v.presentation) {
      if (chunk.foregroundColorId != null ||
          chunk.fontType != Font.PLAIN ||
          chunk.effectType != null ||
          chunk.effectColor != null) {
        data.highlightedRegions.add(
          HighlightedRegion(offset, offset + chunk.text.length, chunk.toTextChunk().attributes)
        )
      }
      offset += chunk.text.length
    }
  }

  override fun canNavigate(): Boolean = value != null

  override fun navigate(requestFocus: Boolean) {
    val v = checkValue()
    OpenFileDescriptor(project, v.file, v.navigationOffset).navigate(requestFocus)
  }

  override fun getVirtualFile(): VirtualFile {
    val v = checkValue()
    return v.file
  }

  override fun getNavigationOffset(): Int {
    val v = checkValue()
    return v.navigationOffset
  }

  override fun createNavigatable(@NotNull project: Project): Navigatable {
    val v = checkValue()
    return OpenFileDescriptor(project, v.file, v.navigationOffset)
  }
}