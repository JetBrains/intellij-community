// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import com.intellij.ide.plugins.PluginInitContextFactory
import com.intellij.ide.plugins.PluginInitializationContext
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber

open class TestPluginInitContextFactory(val initContext: PluginInitializationContext) : PluginInitContextFactory {
  override fun createActualContext(): PluginInitializationContext = initContext

  override fun getContextForEffectiveModuleLoadingRuleDetermination(): PluginInitializationContext = initContext

  override fun createMockContextWithOverrides(
    buildNumberOverride: BuildNumber?,
    disabledPluginsOverride: Set<PluginId>?,
    expiredPluginsOverride: Set<PluginId>?,
    brokenPluginVersionsOverride: Map<PluginId, Set<String>>?,
  ): PluginInitializationContext {
    error("unexpected call")
  }
}
