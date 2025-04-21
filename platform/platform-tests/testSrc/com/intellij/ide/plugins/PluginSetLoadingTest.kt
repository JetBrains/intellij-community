// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.rules.InMemoryFsRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test

// run with intellij.idea.ultimate.tests.main classpath
class PluginSetLoadingTest {
  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  private val rootPath get() = inMemoryFs.fs.getPath("/")
  private val pluginsDirPath get() = rootPath.resolve("wd/plugins")

  @Test
  fun `package prefix collision prevents plugin from loading`() {
    PluginManagerCore.getAndClearPluginLoadingErrors()
    // FIXME these plugins are not related, but one of them loads => depends on implicit order
    PluginBuilder().noDepends().id("foo")
      .module("foo.module", PluginBuilder().noDepends().packagePrefix("common.module"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginsDirPath.resolve("foo"))
    PluginBuilder().noDepends().id("bar")
      .module("bar.module", PluginBuilder().noDepends().packagePrefix("common.module"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginsDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo")
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).hasSizeGreaterThan(0)
    assertThat(errors[0].toString()).contains("conflicts with", "bar.module", "foo.module", "package prefix")
  }

  @Test
  fun `package prefix collision prevents plugin from loading - same plugin`() {
    PluginManagerCore.getAndClearPluginLoadingErrors()
    PluginBuilder().noDepends().id("foo").packagePrefix("common.module")
      .module("foo.module", PluginBuilder().noDepends().packagePrefix("common.module"), loadingRule = ModuleLoadingRule.REQUIRED)
      .build(pluginsDirPath.resolve("foo"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).doesNotHaveEnabledPlugins()
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).hasSizeGreaterThan(0)
    assertThat(errors[0].toString()).contains("conflicts with", "foo.module", "package prefix")
  }

  @Test
  fun `package prefix collision does not prevent plugin from loading if module is optional`() {
    PluginManagerCore.getAndClearPluginLoadingErrors()
    PluginBuilder().noDepends().id("foo")
      .module("foo.module", PluginBuilder().noDepends().packagePrefix("common.module"), loadingRule = ModuleLoadingRule.OPTIONAL)
      .build(pluginsDirPath.resolve("foo"))
    PluginBuilder().noDepends().id("bar")
      .module("bar.module", PluginBuilder().noDepends().packagePrefix("common.module"), loadingRule = ModuleLoadingRule.OPTIONAL)
      .build(pluginsDirPath.resolve("bar"))
    val pluginSet = buildPluginSet()
    assertThat(pluginSet).hasExactlyEnabledPlugins("foo", "bar")
    // FIXME these plugins are not related, but one of them loads => depends on implicit order
    assertThat(pluginSet).hasExactlyEnabledModulesWithoutMainDescriptors("foo.module")
    val errors = PluginManagerCore.getAndClearPluginLoadingErrors()
    assertThat(errors).isNotEmpty()
    assertThat(errors[0].toString()).contains("conflicts with", "bar", "foo.module", "package prefix")
  }

  private fun buildPluginSet(builder: PluginSetTestBuilder.() -> Unit = {}): PluginSet = PluginSetTestBuilder(pluginsDirPath).apply(builder).build()
}