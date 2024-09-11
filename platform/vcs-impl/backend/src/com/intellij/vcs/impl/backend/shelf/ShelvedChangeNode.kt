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
import org.jetbrains.annotations.Nls
import java.awt.Color

internal class ShelvedChangeNode(
  shelvedChange: ShelvedWrapper,
  filePath: FilePath,
  additionalText: @Nls String?
) : ChangesBrowserNode<ShelvedWrapper?>(shelvedChange), Comparable<ShelvedChangeNode?> {
  private val myShelvedChange: ShelvedWrapper
  private val myFilePath: FilePath
  private val myAdditionalText: @Nls String?

  init {
    myShelvedChange = shelvedChange
    myFilePath = filePath
    myAdditionalText = additionalText
  }

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val path = myShelvedChange.getRequestName()
    val directory = StringUtil.defaultIfEmpty(PathUtil.getParentPath(path), VcsBundle.message("shelve.default.path.rendering"))
    val fileName = StringUtil.defaultIfEmpty(PathUtil.getFileName(path), path)

    renderer.append(fileName, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, myShelvedChange.getFileStatus().getColor()))
    if (myAdditionalText != null) {
      renderer.append(FontUtil.spaceAndThinSpace() + myAdditionalText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
    if (renderer.isShowFlatten()) {
      renderer.append(FontUtil.spaceAndThinSpace() + FileUtil.toSystemDependentName(directory), SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    renderer.setIcon(FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon())
  }

  override fun getTextPresentation(): String {
    return PathUtil.getFileName(myShelvedChange.getRequestName())
  }

  override fun isFile(): Boolean {
    return true
  }

  override fun compareTo(o: ShelvedChangeNode): Int {
    return compareFilePaths(myFilePath, o.myFilePath)
  }

  override fun getBackgroundColor(project: Project): Color? {
    return getBackgroundColorFor(project, myFilePath)
  }
}
