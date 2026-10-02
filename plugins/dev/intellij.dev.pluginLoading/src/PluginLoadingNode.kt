// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import com.intellij.ide.plugins.DescriptorExclusionReason
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginDescriptorLoadingError
import com.intellij.ide.plugins.PluginInitializationDiagnosticUtils
import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.plugins.PluginsSourceContext
import com.intellij.ide.plugins.shortLogDescription
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * The user object of every node of the plugin loading state tree.
 *
 * [toString] prints the node on one line. The copy action of the tree copies that line, and the report is made of these lines.
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
  ) : PluginLoadingNode {
    override fun toString(): String = buildString {
      append("[$state] ${descriptor.shortLogDescription}")
      reason?.let { append(" -- ${reasonLabel(it)}") }
    }
  }

  /** A counted holder for the children of one kind. */
  @ApiStatus.Internal
  class GroupNode(val group: NodeGroup, val childCount: Int) : PluginLoadingNode {
    /** The title of the group and the child count, for example `Dependencies (3)`. */
    val label: @Nls String
      get() = "${group.title} ($childCount)"

    override fun toString(): String = label
  }

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
  ) : PluginLoadingNode {
    /** The kind and the id, for example `module foo (jetbrains)`. */
    val label: @NlsSafe String
      get() = when (kind) {
        DependencyKind.MODULE -> "module $id ($namespace)"
        DependencyKind.PLUGIN -> "plugin $id"
        DependencyKind.DEPENDS -> "<depends> $id"
      }

    override fun toString(): String = buildString {
      append(label)
      if (optional) {
        append(" (optional)")
      }
      append(" -> ${target?.shortLogDescription ?: "unresolved"}")
      targetState?.let { append(" [$it]") }
      targetReason?.let { append(" -- ${reasonLabel(it)}") }
    }
  }

  /** One hop of the path from a node that did not load down to the first descriptor that failed. */
  @ApiStatus.Internal
  class ExclusionChainNode(
    val descriptor: IdeaPluginDescriptorImpl,
    val reason: DescriptorExclusionReason,
    /** True for the last hop. That hop is the first descriptor that failed. */
    val isRootCause: Boolean,
  ) : PluginLoadingNode {
    override fun toString(): String = if (isRootCause) "${reasonLabel(reason)}   <-- root cause" else reasonLabel(reason)
  }

  /** A descriptor file that the platform failed to read. */
  @ApiStatus.Internal
  class DescriptorReadErrorNode(val error: PluginDescriptorLoadingError) : PluginLoadingNode {
    override fun toString(): String = "read error: ${error.path}"
  }
}

/**
 * The reason on one line.
 *
 * A dependency cycle message spans several lines. A tree row shows only the first one, and the report indents by nesting.
 */
internal fun reasonLabel(reason: DescriptorExclusionReason): @NlsSafe String =
  PluginInitializationDiagnosticUtils.getLogMessage(reason)
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString(separator = " ")
