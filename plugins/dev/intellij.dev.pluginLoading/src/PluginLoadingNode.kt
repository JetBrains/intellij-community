// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import com.intellij.ide.plugins.DescriptorExclusionReason
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginDescriptorLoadingError
import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.plugins.PluginsSourceContext
import org.jetbrains.annotations.ApiStatus

/**
 * The user object of every node of the plugin loading state tree.
 */
@ApiStatus.Internal
sealed interface PluginLoadingNode {
  /** A plugin, a `<depends>` config or a content module. */
  @ApiStatus.Internal
  class DescriptorNode(
    val descriptor: IdeaPluginDescriptorImpl,
    val state: DescriptorLoadState,
    /** The origin of the plugin. It is `null` for a `<depends>` config and for a content module. */
    val source: PluginsSourceContext?,
    /** The exclusion reason. It is `null` when the descriptor is resolved. */
    val reason: DescriptorExclusionReason?,
  ) : PluginLoadingNode

  /** A counted holder for the children of one kind. */
  @ApiStatus.Internal
  class GroupNode(val group: NodeGroup, val childCount: Int) : PluginLoadingNode

  /** One declared dependency of a descriptor, together with the descriptor the id resolves to. */
  @ApiStatus.Internal
  class DependencyNode(
    val kind: DependencyKind,
    val id: String,
    /** The namespace of a content module id. It is `null` for every other kind. */
    val namespace: String?,
    val optional: Boolean,
    /** The descriptor the id resolves to, or `null` when no descriptor declares the id. */
    val target: PluginModuleDescriptor?,
    val targetState: DescriptorLoadState?,
    /** The exclusion reason of the target. It is `null` when the target is `null` or loaded. */
    val targetReason: DescriptorExclusionReason?,
  ) : PluginLoadingNode

  /** One hop of the path from a node that did not load down to the first descriptor that failed. */
  @ApiStatus.Internal
  class ExclusionChainNode(
    val descriptor: IdeaPluginDescriptorImpl,
    val reason: DescriptorExclusionReason,
    /** True for the last hop. That hop is the first descriptor that failed. */
    val isRootCause: Boolean,
  ) : PluginLoadingNode

  /** A descriptor file that the platform failed to read. */
  @ApiStatus.Internal
  class DescriptorReadErrorNode(val error: PluginDescriptorLoadingError) : PluginLoadingNode
}
