// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ChangesUtil.CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY
import com.intellij.openapi.vcs.impl.PlatformVcsPathPresenter.getPresentableRelativePath
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import org.jetbrains.annotations.Nls
import java.awt.Color


abstract class AbstractChangesBrowserFilePathNode<U>(userObject: U, val status: FileStatus?) : ChangesBrowserNode<U>(userObject) {
  private val filePath: FilePath get() = filePath(getUserObject())
  private val originInfo: OriginInfo? by lazy(LazyThreadSafetyMode.NONE) { buildOriginInfo() }

  protected abstract fun filePath(userObject: U): FilePath

  protected open fun originPath(userObject: U): FilePath? = null

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
      appendOriginText(renderer)
      appendParentPath(renderer, path.parentPath)
    }
    else {
      renderer.append(getRelativePath(path), textAttributes)
      appendOriginText(renderer)
    }
    if (!isLeaf) {
      appendCount(renderer)
    }
    renderer.setIcon(path, path.isDirectory || !isLeaf)
  }

  override fun getBackgroundColor(project: Project): Color? = getBackgroundColorFor(project, filePath)

  private fun appendOriginText(renderer: ChangesBrowserNodeRenderer) {
    originInfo?.let {
      renderer.append(FontUtil.spaceAndThinSpace() + it.getText(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
  }

  private fun buildOriginInfo(): OriginInfo? {
    val originPath = originPath(getUserObject()) ?: return null
    val path = filePath
    val areParentsEqual = CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(path.parentPath, originPath.parentPath)

    return when {
      !areParentsEqual -> OriginInfo.Moved(getPresentableRelativePath(path, originPath))
      path.name != originPath.name -> OriginInfo.Renamed(originPath.name)
      else -> null
    }
  }

  private val textAttributes: SimpleTextAttributes
    get() = if (status != null) SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, status.color)
    else SimpleTextAttributes.REGULAR_ATTRIBUTES

  protected open fun getRelativePath(path: FilePath): @NlsSafe String {
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

  override fun compareUserObjects(o2: U): Int {
    return compareFilePaths(filePath, filePath(o2))
  }

  companion object {
    @JvmStatic
    fun safeCastToFilePath(node: ChangesBrowserNode<*>?): FilePath? {
      if (node == null) return null
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

open class ChangesBrowserFilePathNode(userObject: FilePath, status: FileStatus?) :
  AbstractChangesBrowserFilePathNode<FilePath>(userObject, status) {
  constructor(userObject: FilePath) : this(userObject, null)

  override fun filePath(userObject: FilePath) = userObject
}

private sealed class OriginInfo {
  class Moved(val originRelativePath: @NlsSafe String) : OriginInfo()

  class Renamed(val originName: @NlsSafe String) : OriginInfo()
}

private fun OriginInfo.getText(): @Nls String =
  when (this) {
    is OriginInfo.Moved -> VcsBundle.message("change.file.moved.from.text", originRelativePath)
    is OriginInfo.Renamed -> VcsBundle.message("change.file.renamed.from.text", originName)
  }