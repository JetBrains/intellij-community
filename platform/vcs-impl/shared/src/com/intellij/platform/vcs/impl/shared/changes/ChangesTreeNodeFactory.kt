// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.changes.ui.ChangeListRemoteState
import com.intellij.openapi.vcs.changes.ui.ChangeNodeDecorator
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeListNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import org.jetbrains.annotations.ApiStatus

/**
 * Factory to customize nodes used in the VCS Changes tree.
 *
 * This is an internal SPI used by platform and plugins to override node types for specific contexts
 * (e.g. provide a special ChangeList node and/or child Change nodes under it).
 */
@ApiStatus.Internal
interface ChangesTreeNodeFactory {
  /**
   * Create a node for the given change list. Return null to use the default node type.
   */
  fun createChangeListNode(project: Project,
                           list: ChangeList,
                           listRemoteState: ChangeListRemoteState): ChangesBrowserChangeListNode?

  /**
   * Create a node for the given change when it is inserted under [parent].
   * Return null to use the default node type.
   */
  fun createChangeNode(project: Project?,
                       change: Change,
                       decorator: ChangeNodeDecorator?,
                       parent: ChangesBrowserNode<*>): ChangesBrowserNode<*>?

  companion object {
    private val EP_NAME: ExtensionPointName<ChangesTreeNodeFactory> = ExtensionPointName.create("com.intellij.vcs.changes.changesTreeNodeFactory")

    fun createChangeListNode(project: Project,
                             list: ChangeList,
                             listRemoteState: ChangeListRemoteState): ChangesBrowserChangeListNode?{
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createChangeListNode(project, list, listRemoteState) }
    }

    fun createChangeNode(project: Project?,
                         change: Change,
                         decorator: ChangeNodeDecorator?,
                         parent: ChangesBrowserNode<*>): ChangesBrowserNode<*>?{
      return EP_NAME.extensionList.firstNotNullOfOrNull { it.createChangeNode(project, change, decorator, parent) }
    }

  }
}
