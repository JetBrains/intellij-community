// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.todo.TodoTreeBuilder
import com.intellij.ide.todo.model.TodoGrouping
import com.intellij.ide.todo.model.TodoGrouping.Key
import com.intellij.ide.todo.rpc.TodoFileResult
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.project.Project

internal class TodoRemoteGroupingNode(
  project: Project,
  private val group: TodoGrouping,
  builder: TodoTreeBuilder,
) : BaseToDoNode<Key>(project, group.key, builder) {

  override fun getChildren(): Collection<AbstractTreeNode<*>> = createChildren(project, group, myBuilder)

  override fun contains(element: Any?): Boolean {
    if (element is Key) return group.contains(element)
    val file = when (element) {
      is TodoRemoteFileNode.Value -> element.file
      is TodoRemoteItemNode -> element.value?.file
      else -> null
    }
    val fileId = file?.let { myBuilder.getCachedRemoteTodoFile(it)?.fileId }
    return fileId != null && group.contains(fileId)
  }

  override fun update(presentation: PresentationData) {
    presentation.presentableText = group.name
    presentation.locationString = IdeBundle.message("node.todo.group", group.todoItemCount)
    presentation.setIcon(when (group.key) {
      Key.Root -> null
      is Key.Module -> AllIcons.Nodes.Module
      is Key.Package -> AllIcons.Nodes.Package
      is Key.Directory -> AllIcons.Nodes.Folder
    })
  }

  override fun getFileCount(value: Key?): Int = group.fileCount

  override fun getTodoItemCount(value: Key?): Int = group.todoItemCount

  override fun getWeight(): Int = getWeight(group.key)

  companion object {
    @JvmStatic
    fun createChildren(project: Project, group: TodoGrouping, builder: TodoTreeBuilder): Collection<AbstractTreeNode<*>> {
      val children = ArrayList<AbstractTreeNode<*>>(group.groups.size + group.files.size)
      group.groups
        .sortedWith(compareBy<TodoGrouping> { getWeight(it.key) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        .mapTo(children) { TodoRemoteGroupingNode(project, it, builder) }

      val showPackages = builder.todoTreeStructure.isPackagesShown()
      val fileComparator = if (showPackages) {
        compareBy(String.CASE_INSENSITIVE_ORDER, TodoFileResult::name).thenBy { it.presentableUrl }
      }
      else {
        compareBy(String.CASE_INSENSITIVE_ORDER, TodoFileResult::presentableUrl)
      }
      group.files
        .sortedWith(fileComparator)
        .forEach { result ->
          val file = result.fileId.virtualFile()
          if (file != null && file.isValid) {
            children.add(TodoRemoteFileNode(project, TodoRemoteFileNode.Value(file), builder, false))
          }
        }
      return children
    }

    private fun getWeight(key: Key): Int = when (key) {
      Key.Root -> 0
      is Key.Module -> 1
      is Key.Directory -> 2
      is Key.Package -> 3
    }
  }
}