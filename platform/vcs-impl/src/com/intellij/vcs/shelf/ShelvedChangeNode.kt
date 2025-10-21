// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.shelf.ShelvedWrapper
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.util.PathUtil
import java.awt.Color

internal class ShelvedChangeNode(
  val shelvedChange: ShelvedWrapper,
  val filePath: FilePath,
  val additionalText: String?,
) : ChangesBrowserNode<ShelvedWrapper>(shelvedChange), Comparable<ShelvedChangeNode> {

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
