// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.shelf.tree

import com.intellij.ui.SimpleTextAttributes
import com.intellij.platform.vcs.impl.shared.rhizome.TagNodeEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class TagNode(private val entity: TagNodeEntity, private val attributes: SimpleTextAttributes) : EntityChangesBrowserNode<TagNodeEntity>(entity) {

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    renderer.append(entity.text, attributes)
    appendCount(renderer)
  }

  override fun doGetTextPresentation(): @Nls String? {
    return getUserObject().text
  }
}