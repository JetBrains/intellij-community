// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangeListRemoteState
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import org.jetbrains.annotations.ApiStatus

/**
 * Allows providing partial support for specific node types for [TreeModelBuilder] in RD mode
 */
@ApiStatus.Internal
interface TreeModelBuilderEx {
  /**
   * @return decorator for changes nodes when creating a changelist node
   */
  fun getChangeNodeInChangelistBaseDecorator(listRemoteState: ChangeListRemoteState, change: Change, index: Int): ChangeNodeDecorator?

  fun modifyTreeModelBuilder(modelBuilder: TreeModelBuilder)

  companion object {
    @JvmStatic
    fun getInstanceOrNull(project: Project): TreeModelBuilderEx? = project.serviceOrNull()
  }
}