// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginDependencyAnalysis.DependencyRef
import com.intellij.ide.plugins.PluginInitializationContext
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.plugins.PluginModuleId
import com.intellij.ide.plugins.ProductPluginInitContext.Companion.configureProductModeModules
import com.intellij.ide.plugins.ProductPluginInitContext.Companion.defaultProductCompatibilityDependenciesProvider
import com.intellij.ide.plugins.ProductPluginInitContext.Companion.defaultProductRulesImposedExclusions
import com.intellij.ide.plugins.ProductPluginInitContext.Companion.defaultRuntimeModuleGroupAffiliation
import com.intellij.ide.plugins.ProductPluginInitContext.Companion.defaultShouldIncludeContentModulesForDependsEdgeTarget
import com.intellij.ide.plugins.ProductRulesImposedExclusion
import com.intellij.ide.plugins.UnambiguousPluginSet
import com.intellij.openapi.extensions.PluginId

abstract class PseudoProductTestPluginInitContext : EmptyTestPluginInitContext() {
  abstract val expiredPlugins: Set<PluginId>

  override val environmentConfiguredModules: Map<PluginModuleId, PluginInitializationContext.EnvironmentConfiguredModuleData> by lazy {
    buildMap {
      configureProductModeModules(currentProductModeId)
    }
  }

  override fun provideModuleExclusionsImposedByProductRules(
    pluginSet: UnambiguousPluginSet
  ): Sequence<Pair<PluginModuleDescriptor, ProductRulesImposedExclusion.ProductRulesImposedExclusionReason>> =
    defaultProductRulesImposedExclusions(pluginSet, expiredPlugins, thirdPartyPluginsWithoutConsentCheckResult = null)

  override fun provideCompatibilityDependencies(
    descriptor: IdeaPluginDescriptorImpl,
    pluginSet: UnambiguousPluginSet,
  ): Sequence<DependencyRef> =
    defaultProductCompatibilityDependenciesProvider(descriptor, pluginSet)

  override fun provideCustomRuntimeModuleGroupAffiliation(
    module: PluginModuleDescriptor,
    pluginSet: UnambiguousPluginSet,
  ): PluginModuleDescriptor? =
    defaultRuntimeModuleGroupAffiliation(module, pluginSet)

  override fun shouldIncludeContentModulesForDependsEdgeTarget(resolvedTarget: PluginMainDescriptor): Boolean =
    defaultShouldIncludeContentModulesForDependsEdgeTarget(resolvedTarget)
}
