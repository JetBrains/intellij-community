// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf;

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.PathUtil
import java.awt.Color

internal class ShelvedChangeNode(
  private val shelvedChange: ShelvedWrapper,
  private val filePath: FilePath,
  private val additionalText: String?,
) : ChangesBrowserNode<ShelvedWrapper>(shelvedChange), Comparable<ShelvedChangeNode> {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val path = shelvedChange.requestName
    val directory = StringUtil.defaultIfEmpty(PathUtil.getParentPath(path), VcsBundle.message("shelve.default.path.rendering"))
    val fileName = StringUtil.defaultIfEmpty(PathUtil.getFileName(path), path)

    renderer.append(fileName, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, shelvedChange.fileStatus.getColor()))
    if (additionalText != null) {
      renderer.append(FontUtil.spaceAndThinSpace() + additionalText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
    if (renderer.isShowFlatten) {
      renderer.append(FontUtil.spaceAndThinSpace() + FileUtil.toSystemDependentName(directory), SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    renderer.setIcon(FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon())
  }

  override fun getTextPresentation(): String {
    return PathUtil.getFileName(shelvedChange.requestName)
  }

  override fun isFile(): Boolean {
    return true
  }

  override fun compareTo(o: ShelvedChangeNode): Int {
    return compareFilePaths(filePath, o.filePath)
  }


  override fun getBackgroundColor(project: Project): Color? {
    return getBackgroundColorFor(project, filePath)
  }
}
