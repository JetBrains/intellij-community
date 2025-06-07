// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FileStatus
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.PathUtil
import com.intellij.platform.vcs.impl.frontend.VcsFrontendBundle
import com.intellij.platform.vcs.impl.frontend.changes.findFileStatusById
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class ShelvedChangeNode(val entity: ShelvedChangeEntity) : EntityChangesBrowserNode<ShelvedChangeEntity>(entity) {
  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val path = entity.filePath
    val directory = StringUtil.defaultIfEmpty(PathUtil.getParentPath(path), VcsFrontendBundle.message("shelve.default.path.rendering"))
    val fileName = StringUtil.defaultIfEmpty(PathUtil.getFileName(path), path)
    val fileStatus = findFileStatusById(entity.fileStatus) ?: FileStatus.MODIFIED
    renderer.append(fileName, SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, fileStatus.color))
    if (entity.additionalText != null) {
      renderer.append(FontUtil.spaceAndThinSpace() + entity.additionalText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
    if (renderer.isShowFlatten) {
      renderer.append(FontUtil.spaceAndThinSpace() + FileUtil.toSystemDependentName(directory), SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    renderer.icon = FileTypeManager.getInstance().getFileTypeByFileName(fileName).getIcon()
  }

  override fun doGetTextPresentation(): @Nls String? {
    return PathUtil.getFileName(getUserObject().filePath)
  }
}