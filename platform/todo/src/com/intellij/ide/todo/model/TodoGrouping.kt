// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.model

import com.intellij.ide.todo.rpc.TodoDirectoryResult
import com.intellij.ide.todo.rpc.TodoFileResult
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.psi.util.QualifiedName

internal class TodoGrouping private constructor(
  val key: Key,
  val name: String,
  val groups: List<TodoGrouping>,
  val files: List<TodoFileResult>,
) {
  private val groupKeys: Set<Key> = buildSet {
    add(key)
    groups.forEach { addAll(it.groupKeys) }
  }
  private val fileIds: Set<VirtualFileId> = buildSet {
    files.mapTo(this) { it.fileId }
    groups.forEach { addAll(it.fileIds) }
  }

  val fileCount: Int = files.size + groups.sumOf { it.fileCount }
  val todoItemCount: Int = files.sumOf { it.todos.size } + groups.sumOf { it.todoItemCount }

  fun contains(key: Key): Boolean = key in groupKeys

  fun contains(fileId: VirtualFileId): Boolean = fileId in fileIds

  sealed interface Key {
    data object Root : Key
    data class Module(val name: String) : Key
    data class Package(val moduleName: String?, val qualifiedName: String) : Key
    data class Directory(val moduleName: String?, val groupingRootId: VirtualFileId, val directoryId: VirtualFileId) : Key
  }

  companion object {
    fun build(
      files: Collection<TodoFileResult>,
      showModules: Boolean,
      showPackages: Boolean,
      flattenPackages: Boolean,
    ): TodoGrouping {
      val builder = Builder(showModules, showPackages, flattenPackages)
      for (file in files) {
        builder.addFile(file)
      }
      return builder.build()
    }
  }

  private class Builder(
    private val showModules: Boolean,
    private val showPackages: Boolean,
    private val flattenPackages: Boolean,
  ) {
    private val children = linkedMapOf<Key, LinkedHashSet<Key>>(Key.Root to LinkedHashSet())
    private val files = HashMap<Key, MutableList<TodoFileResult>>()
    private val directories = HashMap<Key.Directory, TodoDirectoryResult>()

    fun addFile(file: TodoFileResult) {
      val moduleName = file.moduleName.takeIf { showModules }
      val parent = getModuleGroup(moduleName)
      val group = when {
        !showPackages || file.packageName == "" -> parent
        file.packageName != null -> getPackageGroup(file, moduleName)
        else -> getDirectoryGroup(file, moduleName, parent)
      }
      files.getOrPut(group) { ArrayList() }.add(file)
    }

    fun build(): TodoGrouping {
      for (key in children.keys) {
        if (key !is Key.Package) continue
        val parent = (if (flattenPackages) null else findParentPackage(key))
                     ?: key.moduleName?.let { Key.Module(it) } ?: Key.Root
        children.getValue(parent).add(key)
      }
      return toGroup(Key.Root)
    }

    private fun addGroup(key: Key, parent: Key? = null): Key {
      children.getOrPut(key) { LinkedHashSet() }
      if (parent != null) children.getValue(parent).add(key)
      return key
    }

    private fun getModuleGroup(moduleName: String?): Key {
      return if (moduleName == null) Key.Root else addGroup(Key.Module(moduleName), Key.Root)
    }

    private fun getPackageGroup(file: TodoFileResult, moduleName: String?): Key {
      val packageName = requireNotNull(file.packageName)
      val key = Key.Package(moduleName, packageName)
      addGroup(key)
      if (flattenPackages) return key

      val packageRootName = file.packageRootName?.takeIf { it.isNotEmpty() }?.let(QualifiedName::fromDottedString)
      var parentPackageName = QualifiedName.fromDottedString(packageName).removeLastComponent()
      while (parentPackageName.componentCount > 0 && (packageRootName == null || parentPackageName.matchesPrefix(packageRootName))) {
        addGroup(Key.Package(moduleName, parentPackageName.toString()))
        parentPackageName = parentPackageName.removeLastComponent()
      }
      return key
    }

    private fun findParentPackage(key: Key.Package): Key.Package? {
      var name = QualifiedName.fromDottedString(key.qualifiedName).removeLastComponent()
      while (name.componentCount > 0) {
        val parent = Key.Package(key.moduleName, name.toString())
        if (parent in children) return parent
        name = name.removeLastComponent()
      }
      return null
    }

    private fun getDirectoryGroup(file: TodoFileResult, moduleName: String?, parent: Key): Key {
      val path = file.directoryPath
      val rootId = path.lastOrNull()?.fileId ?: return parent
      var group = parent
      for (directory in path.asReversed()) {
        val key = Key.Directory(moduleName, rootId, directory.fileId)
        directories[key] = directory
        group = addGroup(key, group)
      }
      return group
    }

    private fun toGroup(group: Key, parentPackageName: String? = null): TodoGrouping {
      var visibleGroup = group
      while (visibleGroup is Key.Package && files[visibleGroup].isNullOrEmpty() && children.getValue(visibleGroup).size == 1) {
        visibleGroup = children.getValue(visibleGroup).single()
      }
      val packageName = (visibleGroup as? Key.Package)?.qualifiedName
      val name = when (visibleGroup) {
        Key.Root -> ""
        is Key.Module -> visibleGroup.name
        is Key.Package -> {
          val qualifiedName = QualifiedName.fromDottedString(visibleGroup.qualifiedName)
          if (parentPackageName == null) qualifiedName.toString()
          else qualifiedName.removeHead(QualifiedName.fromDottedString(parentPackageName).componentCount).toString()
        }
        is Key.Directory -> {
          val directory = directories.getValue(visibleGroup)
          if (flattenPackages) directory.presentableUrl else directory.name
        }
      }
      val groups = children.getValue(visibleGroup).map { toGroup(it, packageName) }
      return TodoGrouping(visibleGroup, name, groups, files[visibleGroup].orEmpty())
    }
  }
}