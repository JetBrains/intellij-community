// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import javax.swing.tree.DefaultMutableTreeNode
import org.jetbrains.annotations.ApiStatus

/**
 * The result of [PluginLoadingStateTreeBuilder.buildPluginLoadingStateTree].
 */
@ApiStatus.Internal
class PluginLoadingStateTree(
  val root: DefaultMutableTreeNode,
  /** Lets the dialog jump from a dependency node to the node of the target descriptor. */
  val nodeByDescriptor: Map<IdeaPluginDescriptorImpl, DefaultMutableTreeNode>,
  val view: PluginSetView,
  val pluginCount: Int,
  val loadedPluginCount: Int,
  val problemPluginCount: Int,
  val descriptorReadErrorCount: Int,
)
