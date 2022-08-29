// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test

class OnDemandPluginDescriptorTest {

  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  private val pluginDirPath
    get() = inMemoryFs.fs
      .getPath("/")
      .resolve("plugin")

  companion object {

    @BeforeClass
    @JvmStatic
    fun setUp() {
      System.setProperty(IdeaPluginDescriptorImpl.ON_DEMAND_ENABLED_KEY, true.toString())
    }

    @AfterClass
    @JvmStatic
    fun tearDown() {
      System.setProperty(IdeaPluginDescriptorImpl.ON_DEMAND_ENABLED_KEY, false.toString())
    }
  }

  @Test
  fun testLoadOnDemandPlugin() {
    PluginBuilder()
      .noDepends()
      .id("foo")
      .onDemand()
      .build(pluginDirPath.resolve("foo"))

    PluginBuilder()
      .noDepends()
      .id("bar")
      .pluginDependency("foo")
      .build(pluginDirPath.resolve("bar"))

    assertThat(PluginSetTestBuilder(pluginDirPath).build().enabledPlugins).isEmpty()
  }

  @Test
  fun testDisabledOnDemandPlugin() {
    PluginBuilder()
      .noDepends()
      .id("foo")
      .onDemand()
      .build(pluginDirPath.resolve("foo"))

    PluginBuilder()
      .noDepends()
      .id("bar")
      .onDemand()
      .pluginDependency("foo")
      .build(pluginDirPath.resolve("bar"))

    val pluginSet = PluginSetTestBuilder(pluginDirPath)
      .withDisabledPlugins("foo")
      .withEnabledOnDemandPlugins("bar")
      .build()
    assertThat(pluginSet.enabledPlugins).isEmpty()
  }

  @Test
  fun testLoadEnabledOnDemandPlugin() {
    PluginBuilder()
      .noDepends()
      .id("foo")
      .onDemand()
      .build(pluginDirPath.resolve("foo"))

    val enabledPlugins = PluginSetTestBuilder(pluginDirPath)
      .withEnabledOnDemandPlugins("foo")
      .build()
      .enabledPlugins

    assertThat(enabledPlugins).hasSize(1)
    assertThat(enabledPlugins.single().pluginId.idString).isEqualTo("foo")
  }
}