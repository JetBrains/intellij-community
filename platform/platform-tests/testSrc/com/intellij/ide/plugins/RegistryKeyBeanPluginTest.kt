// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.Ksuid
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

@RunsInEdt
class RegistryKeyBeanPluginTest {

  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()
  private val rootPath
    get() = inMemoryFs.fs.getPath("/")

  @Rule
  @JvmField
  val projectRule = ProjectRule()

  @Rule
  @JvmField
  val runInEdt = EdtRule()

  @Test
  fun `several plugin registry keys are loaded`() {
    val key1 = "test.plugin.registry.key.1"
    val key2 = "test.plugin.registry.key.2"
    val plugin1 = PluginBuilder()
      .id("plugin1")
      .extensions("""<registryKey key="$key1" defaultValue="true" description="sample text"/>""")
    val plugin2 = PluginBuilder()
      .id("plugin2")
      .extensions("""<registryKey key="$key2" defaultValue="true" description="sample text"/>""")
    loadPlugins(plugin1, plugin2)

    assertTrue(Registry.get(key1).asBoolean())
    assertTrue(Registry.get(key2).asBoolean())
  }

  private fun loadPlugins(vararg plugins: PluginBuilder) {
    for (plugin in plugins) {
      loadPluginWithText(plugin, rootPath.resolve(Ksuid.generate()))
    }
  }
}
