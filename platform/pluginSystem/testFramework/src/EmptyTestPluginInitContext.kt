// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import com.intellij.ide.plugins.AmbiguousPluginSet
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.ide.plugins.PluginInitializationContext
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.ide.plugins.PluginsPerProjectConfig
import com.intellij.ide.plugins.ProductRulesImposedExclusion.ProductRulesImposedExclusionReason
import com.intellij.ide.plugins.UnambiguousPluginSet
import com.intellij.openapi.extensions.PluginId


abstract class EmptyTestPluginInitContext : PluginInitializationContext {
  override val essentialPlugins: Set<PluginId> = emptySet()
  override fun isPluginDisabled(id: PluginId): Boolean = false
  override fun isPluginBroken(id: PluginId, version: String?): Boolean = false
  override val requirePlatformAliasDependencyForLegacyPlugins: Boolean = false
  override val checkEssentialPlugins: Boolean = false
  override val explicitPluginSubsetToLoad: Set<PluginId>? = null
  override val disablePluginLoadingCompletely: Boolean = false
  override val pluginsPerProjectConfig: PluginsPerProjectConfig? = null
  override val environmentConfiguredModules: Map<PluginModuleId, PluginInitializationContext.EnvironmentConfiguredModuleData> = emptyMap()

  override fun provideCompatibilityDependencies(
    descriptor: IdeaPluginDescriptorImpl,
    pluginSet: UnambiguousPluginSet,
  ): Sequence<DependencyRef> = emptySequence()

  override fun provideModuleExclusionsImposedByProductRules(pluginSet: UnambiguousPluginSet): Sequence<Pair<PluginModuleDescriptor, ProductRulesImposedExclusionReason>> =
    emptySequence()

  override fun provideCustomRuntimeModuleGroupAffiliation(module: PluginModuleDescriptor, pluginSet: UnambiguousPluginSet): PluginModuleDescriptor? = null

  override fun shouldIncludeContentModulesForDependsEdgeTarget(resolvedTarget: PluginMainDescriptor): Boolean = true

  override fun runConfigurationDuringStartup(totalPluginSet: AmbiguousPluginSet) {}
}