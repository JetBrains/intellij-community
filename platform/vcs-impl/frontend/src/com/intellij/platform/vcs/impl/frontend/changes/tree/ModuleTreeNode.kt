// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes.tree

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.ui.SimpleTextAttributes
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserNodeRenderer
import com.intellij.platform.vcs.impl.frontend.shelf.tree.EntityChangesBrowserNode
import com.intellij.platform.vcs.impl.shared.rhizome.ModuleNodeEntity
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class ModuleTreeNode(entity: ModuleNodeEntity) : EntityChangesBrowserNode<ModuleNodeEntity>(entity) {
  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val module = getUserObject()
    renderer.append(module.name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    appendCount(renderer);

    appendParentPath(renderer, module.rootPath);

    val moduleTypeID = module.moduleType
    if (moduleTypeID == null) {
      renderer.setIcon(ModuleType.EMPTY.icon)
    }
    else {
      val icon = ModuleTypeManager.getInstance().findByID(moduleTypeID).icon
      renderer.setIcon(icon)
    }
  }

  override fun doGetTextPresentation(): @Nls String? {
    return getUserObject().name
  }
}