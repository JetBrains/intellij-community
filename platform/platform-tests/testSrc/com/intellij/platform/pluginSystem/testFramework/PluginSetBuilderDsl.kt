// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import com.intellij.ide.plugins.PluginSet
import com.intellij.platform.testFramework.plugins.PluginBuilderDsl
import com.intellij.platform.testFramework.plugins.PluginSpecBuilder
import com.intellij.platform.testFramework.plugins.installAt
import java.nio.file.Path
import com.intellij.platform.testFramework.plugins.plugin as buildPlugin

/**
 * Provides a DSL for building a set of plugins for testing purposes.
 * @param pluginsDirPath the directory where the plugins will be stored; maybe in an in-memory filesystem obtained via
 *                       [com.intellij.testFramework.rules.InMemoryFsExtension]
 */
fun buildPluginSet(pluginsDirPath: Path, configureClassLoaders: Boolean = true, builder: PluginSetSpecBuilder.() -> Unit): PluginSet {
  builder(PluginSetSpecBuilder(pluginsDirPath))
  return PluginSetTestBuilder.fromPath(pluginsDirPath).build(configureClassLoaders)
}

@PluginBuilderDsl
class PluginSetSpecBuilder internal constructor(private val pluginsDirPath: Path) {
  fun plugin(id: String? = null, body: PluginSpecBuilder.() -> Unit) {
    val pluginSpec = if (id != null) {
      buildPlugin(id, body)
    }
    else {
      buildPlugin(body = body)
    }
    pluginSpec.installAt(pluginsDirPath)
  }
}
