// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.pluginLoading

import com.intellij.ide.plugins.DescriptorExclusionReason
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.plugins.getMainDescriptor
import com.intellij.ide.plugins.isLoaded
import com.intellij.ide.plugins.isResolved
import com.intellij.ide.plugins.sequenceDescriptorExclusionChain
import com.intellij.dev.pluginLoading.PluginLoadingNode.ExclusionChainNode
import java.util.Collections
import java.util.IdentityHashMap
import org.jetbrains.annotations.ApiStatus

/**
 * Answers the state of any descriptor of a [com.intellij.ide.plugins.PluginSet] without a throw.
 *
 * [com.intellij.ide.plugins.ResolvedPluginSet.getExclusionReason] throws for a descriptor of a plugin that never
 * entered the resolution. This class checks the candidate subset first.
 */
@ApiStatus.Internal
class PluginSetView(val pluginSet: PluginSet) {
  private val candidates: Set<PluginMainDescriptor> = pluginSet.candidateSubset.plugins.toHashSet()

  fun isCandidate(descriptor: IdeaPluginDescriptorImpl): Boolean = descriptor.getMainDescriptor() in candidates

  fun state(descriptor: IdeaPluginDescriptorImpl): DescriptorLoadState {
    if (!isCandidate(descriptor)) {
      return DescriptorLoadState.NOT_A_CANDIDATE
    }
    if (!pluginSet.resolvedPluginSet.isResolved(descriptor)) {
      return DescriptorLoadState.EXCLUDED
    }
    return if (descriptor.isLoaded) DescriptorLoadState.LOADED else DescriptorLoadState.RESOLVED_WITHOUT_CLASS_LOADER
  }

  /** The exclusion reason, but only when the descriptor did not load. */
  fun targetExclusionReason(descriptor: IdeaPluginDescriptorImpl): DescriptorExclusionReason? =
    if (state(descriptor).isProblem) exclusionReason(descriptor) else null

  fun exclusionReason(descriptor: IdeaPluginDescriptorImpl): DescriptorExclusionReason? {
    pluginSet.excludedFromCandidateSubset[descriptor.getMainDescriptor()]?.let { return it }
    if (!isCandidate(descriptor)) {
      return null
    }
    return pluginSet.resolvedPluginSet.getExclusionReason(descriptor)
  }

  /**
   * The path from [descriptor] down to the first descriptor that failed. The last hop is the root cause.
   *
   * The list is empty when the descriptor is resolved, and when the plugin set holds no reason for it.
   */
  fun exclusionChain(descriptor: IdeaPluginDescriptorImpl): List<ExclusionChainNode> {
    if (!state(descriptor).isProblem) {
      return emptyList()
    }
    val visited = Collections.newSetFromMap(IdentityHashMap<IdeaPluginDescriptorImpl, Boolean>())
    val hops = ArrayList<Pair<IdeaPluginDescriptorImpl, DescriptorExclusionReason>>()
    for (link in descriptor.sequenceDescriptorExclusionChain(::exclusionReason)) {
      // a real cycle surfaces as PartOfDependencyCycle, which ends the walk; the guard is insurance
      if (!visited.add(link) || hops.size >= MAX_EXCLUSION_CHAIN_LENGTH) {
        break
      }
      hops.add(link to (exclusionReason(link) ?: break))
    }
    return hops.mapIndexed { index, (link, reason) ->
      ExclusionChainNode(descriptor = link, reason = reason, isRootCause = index == hops.lastIndex)
    }
  }

  /** The effective dependencies. The list is empty unless the descriptor is resolved. */
  fun resolvedDependencies(descriptor: IdeaPluginDescriptorImpl): List<IdeaPluginDescriptorImpl> {
    if (!isCandidate(descriptor) || !pluginSet.resolvedPluginSet.isResolved(descriptor)) {
      return emptyList()
    }
    return pluginSet.resolvedPluginSet.getDirectResolvedDependencies(descriptor)
  }

  /** The effective dependents. The list is empty unless the descriptor is resolved. */
  fun resolvedDependents(descriptor: IdeaPluginDescriptorImpl): List<IdeaPluginDescriptorImpl> {
    if (!isCandidate(descriptor) || !pluginSet.resolvedPluginSet.isResolved(descriptor)) {
      return emptyList()
    }
    return pluginSet.resolvedPluginSet.getDirectResolvedDependents(descriptor)
  }

  companion object {
    /** A guard for the exclusion chain walk. A path this long already means the model is broken. */
    private const val MAX_EXCLUSION_CHAIN_LENGTH = 100
  }
}
