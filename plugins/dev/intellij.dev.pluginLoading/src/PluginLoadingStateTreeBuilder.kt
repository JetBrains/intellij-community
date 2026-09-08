// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import com.intellij.ide.plugins.ContentModuleDescriptor
import com.intellij.ide.plugins.DependsSubDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.plugins.PluginsSourceContext
import com.intellij.ide.plugins.sequenceAllDescriptors
import com.intellij.dev.pluginLoading.PluginLoadingNode.DependencyNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.DescriptorNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.DescriptorReadErrorNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.ExclusionChainNode
import com.intellij.dev.pluginLoading.PluginLoadingNode.GroupNode
import java.util.IdentityHashMap
import javax.swing.tree.DefaultMutableTreeNode
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PluginLoadingStateTreeBuilder {
  /**
   * Builds the tree of the whole plugin set. The root node is invisible.
   *
   * @param problemsOnly keeps a plugin only when the plugin itself or any of its descriptors did not load
   */
  fun buildPluginLoadingStateTree(pluginSet: PluginSet, problemsOnly: Boolean): PluginLoadingStateTree {
    val view = PluginSetView(pluginSet)
    val root = DefaultMutableTreeNode()
    val nodeByDescriptor = IdentityHashMap<IdeaPluginDescriptorImpl, DefaultMutableTreeNode>()

    val readErrors = pluginSet.input.discoveryResult.descriptorLoadingErrors
    if (readErrors.isNotEmpty()) {
      val group = DefaultMutableTreeNode(GroupNode(NodeGroup.DESCRIPTOR_READ_ERRORS, readErrors.size))
      for (error in readErrors) {
        group.add(DefaultMutableTreeNode(DescriptorReadErrorNode(error)))
      }
      root.add(group)
    }

    val sourceByPlugin = IdentityHashMap<PluginMainDescriptor, PluginsSourceContext>()
    for (list in pluginSet.input.discoveryResult.pluginLists) {
      for (plugin in list.plugins) {
        sourceByPlugin.putIfAbsent(plugin, list.source)
      }
    }

    var loadedPluginCount = 0
    var problemPluginCount = 0
    val shownPlugins = ArrayList<Pair<PluginMainDescriptor, Boolean>>(sourceByPlugin.size)
    for (plugin in sourceByPlugin.keys) {
      val hasProblem = plugin.sequenceAllDescriptors().any { view.state(it).isProblem }
      if (hasProblem) problemPluginCount++ else loadedPluginCount++
      if (!problemsOnly || hasProblem) {
        shownPlugins.add(plugin to hasProblem)
      }
    }
    shownPlugins.sortWith(
      compareBy({ !it.second }, { it.first.name.lowercase() }, { it.first.pluginId.idString })
    )

    for ((plugin, _) in shownPlugins) {
      root.add(buildDescriptorNode(plugin, sourceByPlugin[plugin], view, nodeByDescriptor))
    }

    return PluginLoadingStateTree(
      root = root,
      nodeByDescriptor = nodeByDescriptor,
      view = view,
      pluginCount = sourceByPlugin.size,
      loadedPluginCount = loadedPluginCount,
      problemPluginCount = problemPluginCount,
      descriptorReadErrorCount = readErrors.size,
    )
  }

  private fun buildDescriptorNode(
    descriptor: IdeaPluginDescriptorImpl,
    source: PluginsSourceContext?,
    view: PluginSetView,
    nodeByDescriptor: MutableMap<IdeaPluginDescriptorImpl, DefaultMutableTreeNode>,
  ): DefaultMutableTreeNode {
    val state = view.state(descriptor)
    val node = DefaultMutableTreeNode(
      DescriptorNode(
        descriptor = descriptor,
        state = state,
        source = source,
        reason = if (state.isProblem) view.exclusionReason(descriptor) else null,
      )
    )
    nodeByDescriptor[descriptor] = node

    if (descriptor is PluginMainDescriptor) {
      val contentModules = descriptor.contentModules
      if (contentModules.isNotEmpty()) {
        val group = DefaultMutableTreeNode(GroupNode(NodeGroup.CONTENT_MODULES, contentModules.size))
        for (module in contentModules.sortedBy { it.moduleId.name }) {
          group.add(buildDescriptorNode(module, source = null, view = view, nodeByDescriptor = nodeByDescriptor))
        }
        node.add(group)
      }
    }

    val subDescriptors = descriptor.dependencies.mapNotNull { it.subDescriptor }
    if (subDescriptors.isNotEmpty()) {
      val group = DefaultMutableTreeNode(GroupNode(NodeGroup.DEPENDS_CONFIGS, subDescriptors.size))
      for (subDescriptor in subDescriptors) {
        group.add(buildDescriptorNode(subDescriptor, source = null, view = view, nodeByDescriptor = nodeByDescriptor))
      }
      node.add(group)
    }

    val dependencies = collectDependencies(descriptor, view)
    if (dependencies.isNotEmpty()) {
      val group = DefaultMutableTreeNode(GroupNode(NodeGroup.DEPENDENCIES, dependencies.size))
      for (dependency in dependencies) {
        val dependencyNode = DefaultMutableTreeNode(dependency)
        dependency.target?.let { target -> exclusionChainGroup(view.exclusionChain(target))?.let(dependencyNode::add) }
        group.add(dependencyNode)
      }
      node.add(group)
    }

    exclusionChainGroup(view.exclusionChain(descriptor))?.let(node::add)
    return node
  }

  /**
   * Wraps the path into a group node.
   *
   * A path of one hop returns `null`, because the owner node already prints that hop inline.
   */
  private fun exclusionChainGroup(chain: List<ExclusionChainNode>): DefaultMutableTreeNode? {
    if (chain.size < 2) {
      return null
    }
    val group = DefaultMutableTreeNode(GroupNode(NodeGroup.EXCLUSION_CHAIN, chain.size))
    for (hop in chain) {
      group.add(DefaultMutableTreeNode(hop))
    }
    return group
  }

  private fun collectDependencies(descriptor: IdeaPluginDescriptorImpl, view: PluginSetView): List<DependencyNode> {
    val candidateSet = view.pluginSet.candidateSubset
    val result = ArrayList<DependencyNode>()
    for (moduleId in descriptor.moduleDependencies.modules) {
      val target = candidateSet.resolveContentModuleId(moduleId)
      result.add(DependencyNode(
        kind = DependencyKind.MODULE,
        id = moduleId.name,
        namespace = moduleId.namespace,
        optional = false,
        target = target,
        targetState = target?.let(view::state),
        targetReason = target?.let(view::targetExclusionReason),
      ))
    }
    for (pluginId in descriptor.moduleDependencies.plugins) {
      val target = candidateSet.resolvePluginId(pluginId)
      result.add(DependencyNode(
        kind = DependencyKind.PLUGIN,
        id = pluginId.idString,
        namespace = null,
        optional = false,
        target = target,
        targetState = target?.let(view::state),
        targetReason = target?.let(view::targetExclusionReason),
      ))
    }
    for (dependency in descriptor.dependencies) {
      val target = candidateSet.resolvePluginId(dependency.pluginId)
      result.add(DependencyNode(
        kind = DependencyKind.DEPENDS,
        id = dependency.pluginId.idString,
        namespace = null,
        optional = dependency.isOptional,
        target = target,
        targetState = target?.let(view::state),
        targetReason = target?.let(view::targetExclusionReason),
      ))
    }
    return result
  }

  /** The identifier shown for a descriptor. It never localizes, because it comes from a descriptor file. */
  internal val IdeaPluginDescriptorImpl.loadingStateNodeId: String
    get() = when (this) {
      is PluginMainDescriptor -> pluginId.idString
      is ContentModuleDescriptor -> moduleId.displayName
      is DependsSubDescriptor -> descriptorPath
    }
}
