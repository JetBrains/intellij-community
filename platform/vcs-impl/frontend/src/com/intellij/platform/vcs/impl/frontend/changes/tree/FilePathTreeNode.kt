// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes.tree

import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserNodeRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.FontUtil
import com.intellij.util.PlatformIcons
import com.intellij.platform.vcs.impl.frontend.changes.findFileStatusById
import com.intellij.platform.vcs.impl.frontend.shelf.tree.EntityChangesBrowserNode
import com.intellij.platform.vcs.impl.shared.rhizome.FilePathNodeEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class FilePathTreeNode(entity: FilePathNodeEntity) : EntityChangesBrowserNode<FilePathNodeEntity>(entity) {

  private val textAttributes: SimpleTextAttributes by lazy {
    val status = findFileStatusById(entity.status)
    if (status != null) SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, status.color)
    else SimpleTextAttributes.REGULAR_ATTRIBUTES
  }

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val entity: FilePathNodeEntity = getUserObject()
    val name = entity.name
    val originText = entity.originText
    if (entity.parentPath != null) {
      renderer.append(name, textAttributes)
      appendOriginText(originText, renderer)
      appendParentPath(renderer, entity.parentPath)
    }
    else {
      renderer.append(name, textAttributes)
      appendOriginText(originText, renderer)
    }
    if (!isLeaf) {
      appendCount(renderer)
    }

    renderer.icon = PlatformIcons.FOLDER_ICON //TODO add other types of icons if something except shelves will be split
  }

  private fun appendOriginText(@Nls originText: String?, renderer: ChangesBrowserNodeRenderer) {
    if (originText != null) {
      renderer.append(FontUtil.spaceAndThinSpace() + originText, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
  }

  override fun doGetTextPresentation(): @Nls String? {
    return getUserObject().name
  }

}