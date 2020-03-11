// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.ui.SimpleTextAttributes

/**
 * @author yole
 */
open class ChangesBrowserFilePathNode(userObject: FilePath, val status: FileStatus?) : ChangesBrowserNode<FilePath>(userObject) {
  constructor(userObject: FilePath) : this(userObject, null)

  private val filePath: FilePath
    get() = getUserObject()

  override fun isFile(): Boolean {
    return !filePath.isDirectory
  }

  override fun isDirectory(): Boolean {
    return filePath.isDirectory && isLeaf
  }

  override fun render(renderer: ChangesBrowserNodeRenderer,
                      selected: Boolean,
                      expanded: Boolean,
                      hasFocus: Boolean) {
    val path = filePath
    if (renderer.isShowFlatten && isLeaf) {
      renderer.append(path.name, textAttributes)
      appendParentPath(renderer, path.parentPath)
    }
    else {
      renderer.append(getRelativePath(path), textAttributes)
    }
    if (!isLeaf) {
      appendCount(renderer)
    }
    renderer.setIcon(path, path.isDirectory || !isLeaf)
  }

  private val textAttributes: SimpleTextAttributes
    get() = if (status != null) SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, status.color)
    else SimpleTextAttributes.REGULAR_ATTRIBUTES

  protected open fun getRelativePath(path: FilePath): String {
    return getRelativePath(safeCastToFilePath(getParent()), path)
  }

  override fun getTextPresentation(): String {
    return getRelativePath(filePath)
  }

  override fun toString(): String {
    return FileUtil.toSystemDependentName(filePath.path)
  }

  override fun getSortWeight(): Int {
    return if (filePath.isDirectory) DIRECTORY_PATH_SORT_WEIGHT else FILE_PATH_SORT_WEIGHT
  }

  override fun compareUserObjects(o2: FilePath): Int {
    return compareFilePaths(filePath, o2)
  }

  companion object {
    @JvmStatic
    fun safeCastToFilePath(node: ChangesBrowserNode<*>): FilePath? {
      if (node is ChangesBrowserModuleNode) {
        return node.moduleRoot
      }
      val o = node.userObject
      if (o is FilePath) return o
      if (o is Change) return ChangesUtil.getAfterPath(o)
      return null
    }

    fun getRelativePath(parent: FilePath?, child: FilePath): String {
      val isLocal = !child.isNonLocal
      val caseSensitive = isLocal && SystemInfo.isFileSystemCaseSensitive
      var result = if (parent != null) FileUtil.getRelativePath(parent.path, child.path, '/', caseSensitive) else null
      result = result ?: child.path
      return if (isLocal) FileUtil.toSystemDependentName(result) else result
    }
  }
}